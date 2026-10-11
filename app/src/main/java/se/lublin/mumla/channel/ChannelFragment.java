/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.mumla.channel;

import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.PagerTabStrip;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import se.lublin.humla.HumlaService;
import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.TalkState;
import se.lublin.humla.model.WhisperTarget;
import se.lublin.humla.util.HumlaDisconnectedException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.IHumlaObserver;
import se.lublin.humla.util.VoiceTargetMode;
import se.lublin.mumla.R;
import se.lublin.mumla.Settings;
import se.lublin.mumla.app.MumlaActivity;
import se.lublin.mumla.util.HumlaServiceFragment;

/**
 * Class to encapsulate both a ChannelListFragment and ChannelChatFragment.
 * Created by andrew on 02/08/13.
 */
public class ChannelFragment extends HumlaServiceFragment implements SharedPreferences.OnSharedPreferenceChangeListener, ChatTargetProvider {
    private static final String TAG = ChannelFragment.class.getName();

    private ViewPager mViewPager;
    private PagerTabStrip mTabStrip;
    private Button mTalkButton;
    private View mTalkView;

    private View mTargetPanel;
    private ImageView mTargetPanelCancel;
    private TextView mTargetPanelText;

    private ChatTarget mChatTarget;
    /** Chat target listeners, notified when the chat target is changed. */
    private List<OnChatTargetSelectedListener> mChatTargetListeners = new ArrayList<OnChatTargetSelectedListener>();

    // === BARU: Variabel untuk Bottom Control Bar ===
    private View mBottomControlBar;
    private View mBtnAudioMenu;
    private View mBtnSessionMenu;
    private FrameLayout mPttContainer;

    private HumlaObserver mObserver = new HumlaObserver() {
        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (getService() == null || !getService().isConnected()) {
                return;
            }
            int selfSession;
            try {
                selfSession = getService().HumlaSession().getSessionId();
            } catch (HumlaDisconnectedException|IllegalStateException e) {
                Log.d(TAG, "exception in onUserTalkStateUpdated: " + e);
                return;
            }
            if (user != null && user.getSession() == selfSession && mTalkButton != null) {
                mTalkButton.setActivated(isTalkingState(user.getTalkState()));
            }
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            if (getService() == null || !getService().isConnected()) {
                return;
            }
            int selfSession;
            try {
                selfSession = getService().HumlaSession().getSessionId();
            } catch (HumlaDisconnectedException|IllegalStateException e) {
                Log.d(TAG, "exception in onUserStateUpdated: " + e);
                return;
            }
            if (user != null && user.getSession() == selfSession) {
                configureInput();
            }
        }

        @Override
        public void onVoiceTargetChanged(VoiceTargetMode mode) {
            configureTargetPanel();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channel, container, false);
        
        mViewPager = view.findViewById(R.id.channel_view_pager);
        mTabStrip = view.findViewById(R.id.channel_tab_strip);
        if(mTabStrip != null) {
            int[] attrs = new int[] { android.R.attr.colorPrimary, android.R.attr.textColorPrimaryInverse };
            TypedArray a = getActivity().obtainStyledAttributes(attrs);
            int titleStripBackground = a.getColor(0, -1);
            int titleStripColor = a.getColor(1, -1);
            a.recycle();

            mTabStrip.setTextColor(titleStripColor);
            mTabStrip.setTabIndicatorColor(titleStripColor);
            mTabStrip.setBackgroundColor(titleStripBackground);
            mTabStrip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        }

        mTalkView = view.findViewById(R.id.pushtotalk_view);
        mTalkButton = view.findViewById(R.id.pushtotalk);
        mTalkButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        if (getService() != null) getService().onTalkKeyDown();
                        break;
                    case MotionEvent.ACTION_UP:
                        v.setPressed(false);
                        if (getService() != null) getService().onTalkKeyUp();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        if (getService() != null) getService().onTalkKeyCancel();
                        break;
                }
                return true;
            }
        });

        mTargetPanel = view.findViewById(R.id.target_panel);
        mTargetPanelCancel = view.findViewById(R.id.target_panel_cancel);
        mTargetPanelCancel.setOnClickListener(v -> {
            if (getService() == null || !getService().isConnected()) return;
            try {
                IHumlaSession session = getService().HumlaSession();
                if (session.getVoiceTargetMode() == VoiceTargetMode.WHISPER) {
                    byte target = session.getVoiceTargetId();
                    session.setVoiceTargetId((byte) 0);
                    session.unregisterWhisperTarget(target);
                }
            } catch (HumlaDisconnectedException|IllegalStateException e) {
                // Ignore
            }
        });
        mTargetPanelText = view.findViewById(R.id.target_panel_warning);

        // === BARU: Setup Bottom Control Bar ===
        mBottomControlBar = inflater.inflate(R.layout.bottom_control_bar, container, false);
        mBtnAudioMenu = mBottomControlBar.findViewById(R.id.btn_audio_menu);
        mBtnSessionMenu = mBottomControlBar.findViewById(R.id.btn_session_menu);
        mPttContainer = mBottomControlBar.findViewById(R.id.ptt_container);

        // Pindahkan mTalkView (PTT asli) ke dalam container tengah di Bottom Bar
        if (mTalkView != null && mTalkView.getParent() != null) {
            ((ViewGroup) mTalkView.getParent()).removeView(mTalkView);
        }
        if (mPttContainer != null && mTalkView != null) {
            mPttContainer.addView(mTalkView);
        }

        // Tambahkan Bottom Bar ke bagian paling bawah layout utama
        if (view instanceof ViewGroup) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );
            ((ViewGroup) view).addView(mBottomControlBar, params);
        }

        // Setup Klik Tombol Kiri (Audio Menu)
        mBtnAudioMenu.setOnClickListener(v -> showAudioBottomSheet());

        // Setup Klik Tombol Kanan (Session Menu)
        mBtnSessionMenu.setOnClickListener(v -> {
            // Placeholder: Nanti diisi logika Overlay, Refresh, Exit
            android.widget.Toast.makeText(requireContext(), "Menu Sesi (Overlay/Refresh/Exit)", android.widget.Toast.LENGTH_SHORT).show();
        });

        configureInput();
        return view;
    }

    // === BARU: Metode untuk menampilkan Bottom Sheet Audio ===
    private void showAudioBottomSheet() {
        if (getService() == null || !getService().isConnected() || !isAdded()) return;

        BottomSheetDialog bottomSheet = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
        bottomSheet.setContentView(sheetView);

        SwitchCompat switchMute = sheetView.findViewById(R.id.switch_mute);
        SwitchCompat switchDeafen = sheetView.findViewById(R.id.switch_deafen);
        RadioGroup radioInputMode = sheetView.findViewById(R.id.radio_input_mode);
        RadioButton radioVox = sheetView.findViewById(R.id.radio_vox);
        RadioButton radioPtt = sheetView.findViewById(R.id.radio_ptt);
        RadioButton radioContinuous = sheetView.findViewById(R.id.radio_continuous);

        try {
            IUser self = getService().HumlaSession().getSessionUser();
            if (self != null) {
                switchMute.setChecked(self.isSelfMuted() || self.isMuted());
                switchDeafen.setChecked(self.isDeafened());
            }
        } catch (Exception e) {
            Log.d(TAG, "Error getting user state: " + e);
        }

        Settings settings = Settings.getInstance(requireContext());
        String currentInput = settings.getInputMethod();
        if (Settings.ARRAY_INPUT_METHOD_VOICE.equals(currentInput)) radioVox.setChecked(true);
        else if (Settings.ARRAY_INPUT_METHOD_PTT.equals(currentInput)) radioPtt.setChecked(true);
        else if (Settings.ARRAY_INPUT_METHOD_CONTINUOUS.equals(currentInput)) radioContinuous.setChecked(true);

        // Listener Mute
        switchMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try { getService().HumlaSession().setSelfMuted(isChecked); } 
            catch (Exception e) { Log.e(TAG, "Mute error", e); }
        });

        // Listener Deafen
        switchDeafen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try { getService().HumlaSession().setSelfDeafened(isChecked); } 
            catch (Exception e) { Log.e(TAG, "Deafen error", e); }
        });

        // Listener Input Mode
        radioInputMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_vox) {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_VOICE);
            } else if (checkedId == R.id.radio_ptt) {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT);
            } else if (checkedId == R.id.radio_continuous) {
                settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_CONTINUOUS);
            }
            configureInput(); // Refresh tampilan PTT button
            bottomSheet.dismiss();
        });

        bottomSheet.show();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.registerOnSharedPreferenceChangeListener(this);

        if(mViewPager != null) { // Phone
            ChannelFragmentPagerAdapter pagerAdapter = new ChannelFragmentPagerAdapter(getChildFragmentManager());
            mViewPager.setAdapter(pagerAdapter);
        } else { // Tablet
            ChannelListFragment listFragment = new ChannelListFragment();
            Bundle listArgs = new Bundle();
            listArgs.putBoolean("pinned", isShowingPinnedChannels());
            listFragment.setArguments(listArgs);
            ChannelChatFragment chatFragment = new ChannelChatFragment();

            getChildFragmentManager().beginTransaction()
                    .replace(R.id.list_fragment, listFragment)
                    .replace(R.id.chat_fragment, chatFragment)
                    .commit();
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.channel_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem bluetoothItem = menu.findItem(R.id.menu_bluetooth_headset);
        if (bluetoothItem != null && getActivity() != null) {
            boolean requested = Settings.getInstance(getActivity()).isBluetoothHeadset();
            bluetoothItem.setVisible(getService() != null && getService().isConnected());
            bluetoothItem.setChecked(requested);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_bluetooth_headset) {
            if (getActivity() instanceof MumlaActivity) {
                MumlaActivity activity = (MumlaActivity) getActivity();
                activity.setBluetoothHeadset(!Settings.getInstance(activity).isBluetoothHeadset());
            }
            return true;
        }
        Settings settings = Settings.getInstance(getActivity());
        if (itemId == R.id.menu_input_voice) {
            settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_VOICE);
            return true;
        } else if (itemId == R.id.menu_input_ptt) {
            settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT);
            return true;
        } else if (itemId == R.id.menu_input_continuous) {
            settings.setInputMethod(Settings.ARRAY_INPUT_METHOD_CONTINUOUS);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mTalkButton != null) mTalkButton.setPressed(false);
        if (getService() != null) getService().onTalkKeyCancel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mTalkButton != null) {
            mTalkButton.setActivated(false);
            mTalkButton.setPressed(false);
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        
        mViewPager = null;
        mTabStrip = null;
        mTalkButton = null;
        mTalkView = null;
        mTargetPanel = null;
        mTargetPanelCancel = null;
        mTargetPanelText = null;
        // === BARU: Cleanup Bottom Bar ===
        mBottomControlBar = null;
        mBtnAudioMenu = null;
        mBtnSessionMenu = null;
        mPttContainer = null;
    }

    @Override
    public IHumlaObserver getServiceObserver() {
        return mObserver;
    }

    @Override
    public void onServiceBound(IHumlaService service) {
        super.onServiceBound(service);
        if (service.getConnectionState() == HumlaService.ConnectionState.CONNECTED) {
            configureTargetPanel();
            configureInput();
        }
    }

    private void configureTargetPanel() {
        if (!isAdded() || getActivity() == null || mTargetPanel == null || mTargetPanelText == null) return;
        if (getService() == null || !getService().isConnected()) return;

        try {
            IHumlaSession session = getService().HumlaSession();
            VoiceTargetMode mode = session.getVoiceTargetMode();
            WhisperTarget target = mode == VoiceTargetMode.WHISPER ? session.getWhisperTarget() : null;
            if (target != null) {
                mTargetPanel.setVisibility(View.VISIBLE);
                mTargetPanelText.setText(getString(R.string.shout_target, target.getName()));
            } else {
                mTargetPanel.setVisibility(View.GONE);
            }
        } catch (HumlaDisconnectedException|IllegalStateException e) {
            // Ignore
        }
    }

    private boolean isShowingPinnedChannels() {
        return getArguments() != null && getArguments().getBoolean("pinned");
    }

    static int calculateButtonHeightPx(int heightDp, android.util.DisplayMetrics metrics) {
        if (heightDp <= 0 || metrics == null) return heightDp;
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp, metrics);
        if (px > 0) return Math.max(1, Math.round(px));
        float density = metrics.density > 0 ? metrics.density : 1.0f;
        return Math.max(1, Math.round(heightDp * density));
    }

    static boolean isTalkingState(TalkState state) {
        if (state == null) return false;
        return state == TalkState.TALKING || state == TalkState.SHOUTING || state == TalkState.WHISPERING;
    }

    private void configureInput() {
        if (!isAdded() || getActivity() == null || mTalkButton == null) return;
        Settings settings = Settings.getInstance(getActivity());

        int heightDp = settings.getPTTButtonHeight();
        int heightPx = calculateButtonHeightPx(heightDp, getResources() != null ? getResources().getDisplayMetrics() : null);

        ViewGroup.LayoutParams params = mTalkButton.getLayoutParams();
        if (params != null) {
            params.height = heightPx;
            mTalkButton.setLayoutParams(params);
        }

        boolean muted = false;
        if (getService() != null && getService().isConnected()) {
            try {
                IHumlaSession session = getService().HumlaSession();
                mTalkButton.setActivated(session.isTalking());
                IUser self = session.getSessionUser();
                muted = self == null || self.isMuted() || self.isSuppressed() || self.isSelfMuted();
            } catch (HumlaDisconnectedException|IllegalStateException e) {
                Log.d(TAG, "exception in configureInput: " + e);
            }
        } else {
            mTalkButton.setActivated(false);
        }
        
        boolean showPttButton = !muted && settings.isPushToTalkButtonShown() && settings.getInputMethod().equals(Settings.ARRAY_INPUT_METHOD_PTT);
        setTalkButtonHidden(!showPttButton);
    }

    private void setTalkButtonHidden(final boolean hidden) {
        if (mTalkView == null) return;
        mTalkView.setVisibility(hidden ? View.GONE : View.VISIBLE);
        if (hidden && mTalkButton != null) {
            mTalkButton.setActivated(false);
            mTalkButton.setPressed(false);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(Settings.PREF_INPUT_METHOD.equals(key) || Settings.PREF_PUSH_BUTTON_HIDE_KEY.equals(key) || Settings.PREF_PTT_BUTTON_HEIGHT.equals(key)) {
            configureInput();
        }
        if (Settings.PREF_BLUETOOTH_HEADSET.equals(key) && isAdded() && getActivity() instanceof MumlaActivity) {
            ((MumlaActivity) getActivity()).supportInvalidateOptionsMenu();
        }
    }

    @Override
    public ChatTarget getChatTarget() { return mChatTarget; }

    @Override
    public void setChatTarget(ChatTarget target) {
        mChatTarget = target;
        for(OnChatTargetSelectedListener listener : mChatTargetListeners) {
            listener.onChatTargetSelected(target);
        }
    }

    @Override
    public void registerChatTargetListener(OnChatTargetSelectedListener listener) {
        mChatTargetListeners.add(listener);
    }

    @Override
    public void unregisterChatTargetListener(OnChatTargetSelectedListener listener) {
        mChatTargetListeners.remove(listener);
    }

    private class ChannelFragmentPagerAdapter extends FragmentPagerAdapter {
        public ChannelFragmentPagerAdapter(FragmentManager fm) { super(fm); }

        @Override
        public Fragment getItem(int i) {
            Fragment fragment;
            Bundle args = new Bundle();
            if (i == 0) {
                fragment = new ChannelListFragment();
                args.putBoolean("pinned", isShowingPinnedChannels());
            } else {
                fragment = new ChannelChatFragment();
            }
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position == 0) return getString(R.string.channel).toUpperCase();
            if (position == 1) return getString(R.string.chat).toUpperCase();
            return null;
        }

        @Override
        public int getCount() { return 2; }
    }
}