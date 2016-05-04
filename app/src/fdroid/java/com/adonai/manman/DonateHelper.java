package com.adonai.manman;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Another implementation of donate class with direct paypal bindings
 * It's diverged from `release` one because Google forbids any payment methods in its applications but Google Wallet
 *
 * @author Oleg Chernovskiy
 */
public class DonateHelper {
    private Activity mActivity;

    public DonateHelper(Activity activity) {
        mActivity = activity;
    }

    public void purchaseGift() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.donate)
                .setMessage(R.string.thanks_for_pledge)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.donate_via_paypal, new PaypalDonateListener())
                .create().show();
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }

    public void handleActivityDestroy() {
        // nothing to do here :)
    }

    private class PaypalDonateListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            donatePayPalOnClick();
        }


        /**
         * Donate button with PayPal by opening browser with defined URL For possible parameters see:
         * https://developer.paypal.com/webapps/developer/docs/classic/paypal-payments-standard/integration-guide/Appx_websitestandard_htmlvariables/
         *
         */
        public void donatePayPalOnClick() {
            Uri.Builder uriBuilder = new Uri.Builder();
            /* https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=6CFFYXFT6QT46 */
            uriBuilder.scheme("https").authority("www.paypal.com").path("cgi-bin/webscr");
            uriBuilder.appendQueryParameter("cmd", "_s-xclick");
            uriBuilder.appendQueryParameter("hosted_button_id", "6CFFYXFT6QT46");
            Uri payPalUri = uriBuilder.build();
            try {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, payPalUri);
                mActivity.startActivity(viewIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mActivity, R.string.donations_alert_dialog_no_browser, Toast.LENGTH_LONG).show();
            }
        }
    }
}
