package com.adonai.manman;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

import com.android.vending.util.IabHelper;
import com.android.vending.util.IabResult;
import com.android.vending.util.Inventory;
import com.android.vending.util.Purchase;

import java.util.Date;

/**
 * Implementation of donate helper class with Google Play bindings
 *
 * @author Oleg Chernovskiy
 */
public class DonateHelper {
    private static final String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA51neavcx/+qXg/uguvvN3511aPXP6jgPc2Q0+ekGNeS2lNzwpq5+qBywbQ2PIs0DPvLrtiOwpxxNmKn4EH6i9YmmrEa02rVg1DdJnodZarx/Bg28V55YUKSGAWHKCZVrCSy+VXyVu4iBMmpHf/oHsLxeZqpx7s7YAvzJ4mqoDHThf39RLmnwWPKRl2WFnsDBX9vNCchx5xE8OdZXZZI9zkc46JJxeiJa3ypqAqMhiDPX/E3lznKCoavPGH7z/mCXwc63nSW1LmRnViT3Zg/onPtcsc/NyahYfoEllA2Vx8QG709w7sp8MngjxHGJ1ZzFDd22UeaiOvoIKBwzA0BUxwIDAQAB";

    private Activity mActivity;

    // helpers for donations (from android vending tutorial)
    private static final String SKU_DONATE = "small";
    private IabHelper mHelper;
    private boolean mCanBuy = false;

    /**
     * Should be called on activity on-create method
     */
    public DonateHelper(Activity activity) {
        mActivity = activity;
        mHelper = new IabHelper(activity, base64EncodedPublicKey);
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            @Override
            public void onIabSetupFinished(IabResult result) {
                if (result.isSuccess())
                    mCanBuy = true;
            }
        });
    }

    // needed for vending
    public void purchaseGift() {
        if (mCanBuy) {
            mHelper.launchPurchaseFlow(mActivity, SKU_DONATE, 6666, new IabHelper.OnIabPurchaseFinishedListener() {
                @Override
                public void onIabPurchaseFinished(IabResult result, Purchase info) {
                    if (result.isSuccess()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                        builder.setTitle(R.string.completed).setMessage(R.string.thanks_for_pledge);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.create().show();
                    }

                    mHelper.queryInventoryAsync(false, new IabHelper.QueryInventoryFinishedListener() {
                        @Override
                        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
                            if (result.isSuccess()) {
                                if (inv.getPurchase(SKU_DONATE) != null)
                                    mHelper.consumeAsync(inv.getPurchase(SKU_DONATE), null);
                            }
                        }
                    });
                }
            }, "ManManDonation " + new Date());
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        return mHelper.handleActivityResult(requestCode, resultCode, data);
    }

    public void handleActivityDestroy() {
        // needed for vending
        if (mCanBuy)
            mHelper.dispose();
    }
}
