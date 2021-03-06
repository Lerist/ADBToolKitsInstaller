package crixec.adbtoolkitsinstall;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity implements CompoundButton.OnCheckedChangeListener, ShellUtils.Result, AdapterView.OnItemSelectedListener {

    private CheckBox adbCheckBox;
    private CheckBox fastbootCheckBox;
    private Button installButton;
    private StringBuilder output = new StringBuilder();
    private Spinner spinner;
    private ArrayAdapter<String> adapter;
    private List<String> locations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        adbCheckBox = (CheckBox) findViewById(R.id.adb);
        fastbootCheckBox = (CheckBox) findViewById(R.id.fastboot);
        installButton = (Button) findViewById(R.id.install);
        spinner = (Spinner) findViewById(R.id.spinner);
        adbCheckBox.setOnCheckedChangeListener(this);
        fastbootCheckBox.setOnCheckedChangeListener(this);
        Collections.addAll(locations, getResources().getStringArray(R.array.locations));
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setOnItemSelectedListener(this);
        spinner.setAdapter(adapter);
    }

    private boolean hasCheckedToolKits() {
        return adbCheckBox != null && fastbootCheckBox != null && (adbCheckBox.isChecked() || fastbootCheckBox.isChecked());
    }

    public void installButton(View view) {
        if (hasCheckedToolKits()) {
            new InstallTask(this, adbCheckBox.isChecked(), fastbootCheckBox.isChecked(), locations.get(spinner.getSelectedItemPosition())).execute();
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        installButton.setEnabled(hasCheckedToolKits());
    }

    @Override
    public void onStdout(String text) {
        output.append(text).append("\n");
        Log.i("ADBToolKitsInstaller", text);
    }

    @Override
    public void onStderr(String text) {
        output.append(text).append("\n");
        Log.i("ADBToolKitsInstaller", text);
    }

    @Override
    public void onCommand(String command) {
        output.append(command).append("\n");
        Log.i("ADBToolKitsInstaller", command);
    }

    @Override
    public void onFinish(int resultCode) {
        output.append("======\nexited with code ").append(resultCode).append("\n======\n").append("\n");
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (locations.get(position).equals(getResources().getStringArray(R.array.locations)[3])) {
            // add custom location
            final EditText editText = new EditText(this);
            DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        String location = editText.getText().toString();
                        if (!TextUtils.isEmpty(location)) {
                            locations.add(locations.size() - 1, location);
                            adapter.notifyDataSetChanged();
                            spinner.setSelection(locations.size() - 2);
                            return;
                        } else {
                            spinner.setSelection(0);
                            Toast.makeText(getApplicationContext(), getString(R.string.incorrect_installation_location), Toast.LENGTH_SHORT).show();
                        }
                    }
                    spinner.setSelection(0);
                }
            };
            editText.setHint(R.string.install_location);
            new AlertDialog.Builder(this).setCancelable(false)
                    .setView(editText)
                    .setNeutralButton(android.R.string.cancel, onClickListener)
                    .setPositiveButton(android.R.string.ok, onClickListener)
                    .show();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        spinner.setSelection(0);
    }

    private class InstallTask extends AsyncTask<Void, Void, Boolean> {
        List<Bin> bins = new ArrayList<>();
        private ProgressDialog dialog;
        private AlertDialog.Builder dialogBuilder;

        InstallTask(Context context, boolean installAdb, boolean installFastboot, String targetPath) {
            dialog = new ProgressDialog(context);
            dialogBuilder = new AlertDialog.Builder(context);
            if (installAdb)
                bins.add(new Bin(getApplicationInfo().nativeLibraryDir + "/libadb.so", targetPath + "/adb"));
            if (installFastboot)
                bins.add(new Bin(getApplicationInfo().nativeLibraryDir + "/libfastboot.so", targetPath + "/fastboot"));
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog.setTitle(R.string.installing);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.dismiss), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);
            dialog.dismiss();
            String title = getString(R.string.successful);
            if (!aBoolean)
                title = getString(R.string.failure);
            dialogBuilder.setTitle(title);
            dialogBuilder.setMessage(output.toString());
            dialogBuilder.setPositiveButton(android.R.string.ok, null);
            dialogBuilder.setCancelable(true);
            dialogBuilder.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            output.setLength(0);
            List<String> commands = new ArrayList<>();
            commands.add("mount -o remount,rw /system");
            for (Bin bin : bins) {
                String command1 = String.format("cp -f \'%s\' \'%s\'", bin.getFrom(), bin.getTo());
                commands.add(command1);
                String command2 = String.format("chmod 0755 \'%s\'", bin.getTo());
                commands.add(command2);
            }
            return ShellUtils.exec(commands, MainActivity.this, true) == 0;
        }
    }
}
