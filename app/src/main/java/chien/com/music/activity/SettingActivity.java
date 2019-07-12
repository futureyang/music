package chien.com.music.activity;

import android.preference.PreferenceActivity;

import java.util.List;

import chien.com.music.R;
import chien.com.music.fragment.SettingFragment;

public class SettingActivity extends PreferenceActivity {

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_header, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return SettingFragment.class.getName().equals(fragmentName);
    }
}
