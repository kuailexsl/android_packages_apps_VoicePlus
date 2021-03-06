package org.cyanogenmod.voiceplus;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class VoicePlusSetup extends Activity {
    class AccountAdapter extends ArrayAdapter<Account> {
        AccountAdapter() {
            super(VoicePlusSetup.this, android.R.layout.simple_list_item_single_choice);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            CheckedTextView tv = (CheckedTextView) view.findViewById(android.R.id.text1);
            Account account = getItem(position);
            tv.setText(account.name);

            return view;
        }
    }

    Account NULL;

    ListView lv;
    AccountAdapter accountAdapter;
    SharedPreferences settings;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        accountAdapter = new AccountAdapter();
        settings = getSharedPreferences("settings", MODE_PRIVATE);

        LinearLayout statusContainer = (LinearLayout)findViewById(R.id.status_container);
        TextView status = (TextView)findViewById(R.id.status);
        final String[] permissions = new String[] {
            Manifest.permission.BROADCAST_SMS,
            Manifest.permission.WRITE_SECURE_SETTINGS,
            "android.permission.CANCEL_NOTIFICATIONS",
            "android.permission.INTERCEPT_SMS",
        };
        boolean ok = true;
        for (String permission: permissions) {
            if (checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                status.setText(getString(R.string.not_granted, permission));
                ok = false;
            }
        }
        if (ok)
            statusContainer.setVisibility(View.GONE);

        lv = (ListView) findViewById(R.id.list);
        lv.setAdapter(accountAdapter = new AccountAdapter());

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Account account = accountAdapter.getItem(position);

                final String previousAccount = settings.getString("account", null);
                new Thread() {
                    @Override
                    public void run() {
                        super.run();invalidateToken(previousAccount);
                    }
                }.start();

                if (account == NULL) {
                    settings.edit().remove("account").remove("_rnr_se").commit();
                    return;
                }

                lv.clearChoices();
                lv.requestLayout();
                getToken(account, position);
            }
        });

        String selectedAccount = settings.getString("account", null);

        NULL = new Account(getString(R.string.disable), "com.google");
        accountAdapter.add(NULL);
        int selected = 0;
        for (Account account : AccountManager.get(this).getAccountsByType("com.google")) {
            if (account.name.equals(selectedAccount))
                selected = accountAdapter.getCount();
            accountAdapter.add(account);
        }

        lv.setItemChecked(selected, true);
        lv.requestLayout();

        startService(new Intent(this, VoicePlusService.class));
    }

    void invalidateToken(String account) {
        if (account == null)
            return;

        try {
            // grab the auth token
            Bundle bundle = AccountManager.get(this).getAuthToken(new Account(account, "com.google"), "grandcentral", true, null, null).getResult();
            String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
            AccountManager.get(this).invalidateAuthToken("com.google", authToken);
            Log.i(LOGTAG, "Token invalidated.");
        }
        catch (Exception e) {
            Log.e(LOGTAG, "error invalidating token", e);
        }
    }

    private static final String LOGTAG = "VoicePlusSetup";

    void getToken(final Account account, final int position) {
        AccountManager am = AccountManager.get(this);
        if (am == null)
            return;
        am.getAuthToken(account, "grandcentral", null, this, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                try {
                    Bundle bundle = future.getResult();
                    final String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                    settings.edit()
                    .putString("account", account.name)
                    .commit();
                    Intent intent = new Intent(VoicePlusSetup.this, VoicePlusService.class);
                    intent.setAction(VoicePlusService.ACCOUNT_CHANGED);
                    startService(intent);

                    lv.setItemChecked(position, true);
                    lv.requestLayout();
                    Log.i(LOGTAG, "Token retrieved.");
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }, new Handler());
    }
}
