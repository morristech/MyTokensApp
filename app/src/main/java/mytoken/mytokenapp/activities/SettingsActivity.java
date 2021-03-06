package mytoken.mytokenapp.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.socks.library.KLog;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import mytoken.mytokenapp.BaseActivity;
import mytoken.mytokenapp.BaseApplication;
import mytoken.mytokenapp.R;
import mytoken.mytokenapp.data.local.AppDatabase;
import mytoken.mytokenapp.data.local.PreferencesHelper;
import mytoken.mytokenapp.utils.Cryptography;
import mytoken.mytokenapp.utils.DialogFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

public class SettingsActivity extends BaseActivity {

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getFragmentManager().beginTransaction()
        .replace(android.R.id.content, new MyPreferenceFragment())
        .commit();
    getSupportActionBar().setElevation(0);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        NavUtils.navigateUpFromSameTask(this);
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public static class MyPreferenceFragment extends PreferenceFragment {
    private ProgressDialog progressDialog;

    @Override public void onCreate(final Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.settings);

      Preference version = findPreference("version");
      try {
        String versionName = getActivity().getPackageManager()
            .getPackageInfo(getActivity().getPackageName(), 0).versionName;
        version.setSummary(versionName);
      } catch (PackageManager.NameNotFoundException e) {
        e.printStackTrace();
      }

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
      String network_preference = prefs.getString("network_preference", "mainnet");
      Preference networkPreference = findPreference("network_preference");
      networkPreference.setSummary(network_preference);

      networkPreference.setOnPreferenceChangeListener((preference, newValue) -> {
        preference.setSummary(newValue.toString());
        BaseApplication.web3j = null; //resets the value of web3j so it gets reinitialized
        return true;
      });

      Preference buttonfeedback = findPreference(getString(R.string.send_feedback));
      buttonfeedback.setOnPreferenceClickListener(preference -> {
        DialogFactory.simple_toast(getActivity(), "You should have Telegram installed...")
            .show();
        Intent viewIntent =
            new Intent("android.intent.action.VIEW", Uri.parse("https://t.me/mytokenapp"));
        startActivity(viewIntent);

        return true;
      });

      Preference buttonReset = findPreference(getString(R.string.erase_everything));
      buttonReset.setOnPreferenceClickListener(preference -> {
        PreferencesHelper preferencesHelper = new PreferencesHelper(getActivity());
        preferencesHelper.clear();
        try {
          AppDatabase db =
              BaseApplication.getAppDatabase(getActivity());
          db.clearAllTables();
        }catch (Exception ex){
          KLog.e(ex);
        }
        getActivity().finish();
        getActivity().finishAffinity();
        return true;
      });

      Preference exportWallet = findPreference(getString(R.string.export_wallet));
      exportWallet.setOnPreferenceClickListener(preference -> {

        Cryptography cryptography = new Cryptography(getActivity());
        try {
          PreferencesHelper preferencesHelper = new PreferencesHelper(getActivity());
          String decodedPassword = cryptography.decryptData(preferencesHelper.getPassword());
          String decodedSeed = cryptography.decryptData(preferencesHelper.getSeed());
          Credentials credentials = WalletUtils.loadBip39Credentials(decodedPassword, decodedSeed);

          String privateKey = credentials.getEcKeyPair().getPrivateKey().toString(16);

          showPrivateKeysDialog(getActivity(), privateKey);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException | CertificateException | InvalidAlgorithmParameterException | IOException | InvalidKeyException | NoSuchProviderException | IllegalBlockSizeException | BadPaddingException e) {
          e.printStackTrace();
        }

        return true;
      });

      Preference buttonChegePin = findPreference(getString(R.string.change_pin));
      buttonChegePin.setOnPreferenceClickListener(preference -> {

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
            getActivity());
        LayoutInflater inflater = (LayoutInflater) getActivity()
            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_change_pin, null);
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setCancelable(false);
        final AlertDialog dialog = alertDialogBuilder.create();
        dialog.show();
        Button btn_update_pin_change = view.findViewById(R.id.btn_update_pin_change);
        Button btn_update_pin_close = view.findViewById(R.id.btn_update_pin_close);
        EditText edit_settings_old_pin = view.findViewById(R.id.edit_settings_old_pin);
        EditText edit_settings_new_pin = view.findViewById(R.id.edit_settings_new_pin);
        EditText edit_settings_new_pin2 = view.findViewById(R.id.edit_settings_new_pin2);

        btn_update_pin_close.setOnClickListener(v -> dialog.dismiss());

        btn_update_pin_change.setOnClickListener(view1 -> {

          progressDialog =
              DialogFactory.createProgressDialog(getActivity(), "Updating New Pin...");
          progressDialog.show();
          progressDialog.setCancelable(false);

          // prevent brute forcing
          new CountDownTimer(1000, 1000) {

            @Override public void onTick(long l) { }

            @Override public void onFinish() {
              if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
              }

              String oldPin = edit_settings_old_pin.getText().toString();
              String pin = edit_settings_new_pin.getText().toString();
              String pin2 = edit_settings_new_pin2.getText().toString();
              if (pin.length() < 4) {
                DialogFactory.error_toast(getActivity(), "Pin should be at least 4 characters")
                    .show();
                dialog.dismiss();
                return;
              }

              if (!pin.equals(pin2)) {
                DialogFactory.error_toast(getActivity(), "Pin and Repeated Pin do not match")
                    .show();
                dialog.dismiss();
                return;
              }

              // check the old pin
              PreferencesHelper preferencesHelper = new PreferencesHelper(getActivity());
              String encryptedPIN = preferencesHelper.getPIN();

              Cryptography cryptography = new Cryptography(getActivity());

              try {
                String decryptedPIN = cryptography.decryptData(encryptedPIN);

                if (!decryptedPIN.equals(oldPin)) {
                  DialogFactory.error_toast(getActivity(), "Incorrect Old PIN").show();
                  dialog.dismiss();
                  return;
                }

                // all is good
                String encrypted = cryptography.encryptData(pin);
                preferencesHelper.setPIN(encrypted);
                DialogFactory.success_toast(getActivity(), "Pin Changed Successfully").show();
                dialog.dismiss();
              } catch (NoSuchPaddingException | NoSuchAlgorithmException |
                  UnrecoverableEntryException | CertificateException | KeyStoreException |
                  IOException | InvalidAlgorithmParameterException | InvalidKeyException |
                  NoSuchProviderException | BadPaddingException | IllegalBlockSizeException e) {
                e.printStackTrace();
                DialogFactory.createGenericErrorDialog(getActivity(), e.getLocalizedMessage())
                    .show();
              }
            }
          }.start();
        });

        return true;
      });
    }

    public static void showPrivateKeysDialog(Context context, String textToCopyToClipboard) {

      final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
      LayoutInflater inflater =
          (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      View view = inflater.inflate(R.layout.dialog_private_keys, null);
      alertDialogBuilder.setView(view);
      alertDialogBuilder.setCancelable(false);
      final AlertDialog dialog = alertDialogBuilder.create();
      dialog.show();

      Button btn_dlg_clipboard = view.findViewById(R.id.btn_dlg_clipboard);
      Button btn_dlg_close = view.findViewById(R.id.btn_dlg_close);
      EditText editText_dlg_keys = view.findViewById(R.id.editText_dlg_keys);

      editText_dlg_keys.setText(textToCopyToClipboard);

      btn_dlg_clipboard.setOnClickListener(v -> {

        android.content.ClipboardManager clipboard =
            (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip =
            android.content.ClipData.newPlainText("private", textToCopyToClipboard);
        if (clipboard != null) {
          clipboard.setPrimaryClip(clip);
          DialogFactory.success_toast(context, "Text has been copied to clipboard.").show();
        }
      });
      btn_dlg_close.setOnClickListener(view1 -> dialog.dismiss());
    }
  }
}