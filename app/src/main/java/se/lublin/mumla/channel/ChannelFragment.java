/*
 * Copyright (C) 2014 Andrew Comminos
 * By Jureni123 Modified for Mumla OLED - Stage Layout + Smart Admin Announcement (Auto-Hide Fix)
 */
package se.lublin.mumla.channel;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerTabStrip;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import se.lublin.humla.HumlaService;
import se.lublin.humla.IHumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.TalkState;
import se.lublin.humla.model.WhisperTarget;
import se.lublin.humla.net.Permissions;
import se.lublin.humla.util.HumlaDisconnectedException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.humla.util.IHumlaObserver;
import se.lublin.humla.util.VoiceTargetMode;
import se.lublin.mumla.R;
import se.lublin.mumla.Settings;
import se.lublin.mumla.app.MumlaActivity;
import se.lublin.mumla.service.MumlaService; 
import se.lublin.mumla.util.HumlaServiceFragment;

public class ChannelFragment extends HumlaServiceFragment implements SharedPreferences.OnSharedPreferenceChangeListener, ChatTargetProvider {
    private static final String TAG = "MumlaOLED";

    // Variabel Lama (ViewPager, PTT, dll)
    private ViewPager mViewPager;
    private PagerTabStrip mTabStrip;
    private Button mTalkButton;
    private View mTalkView;
    private View mTargetPanel;
    private ImageView mTargetPanelCancel;
    private TextView mTargetPanelText;

    // Variabel Single Channel View (STAGE LAYOUT)
    private FrameLayout mSingleChContainer;
    private RecyclerView mRvUserList;
    private SingleChannelUserAdapter mSingleUserAdapter;
    private View mHeaderSingleCh;
    
    // Variabel Header Stage
    private TextView mTvChannelId, mTvChannelName, mTvSpeakerName, mTvChannelDesc;
    private ImageView mImgMainAvatar;
    private TextView mTvAnnouncementOverlay;

    // ✅ VARIABEL SMART ADMIN INPUT (SENIOR FRIENDLY)
    private LinearLayout mAdminInputContainer;
    private EditText mEtAdminAnnounce;
    private ImageButton mBtnSendAnnounce;

    private ChatTarget mChatTarget;
    private List<OnChatTargetSelectedListener> mChatTargetListeners = new ArrayList<>();

    // Variabel Bottom Bar
    private View mBottomControlBar;
    private View mBtnGlobalSet, mBtnAudioMenu, mBtnSessionMenu, mBtnListCh;
    private FrameLayout mPttContainer;

    // OBSERVER LENGKAP
    private HumlaObserver mObserver = new HumlaObserver() {
        @Override
        public void onUserConnected(IUser user) {
            Log.d(TAG, ">>> [EVENT] User Connected");
            if (mSingleChContainer != null && mSingleChContainer.getVisibility() == View.VISIBLE) refreshSingleChannelView();
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            Log.d(TAG, ">>> [EVENT] User Joined Channel");
            if (mSingleChContainer != null && mSingleChContainer.getVisibility() == View.VISIBLE) refreshSingleChannelView();
        }

        @Override
        public void onUserRemoved(IUser user, String reason) {
            Log.d(TAG, ">>> [EVENT] User Removed");
            if (mSingleChContainer != null && mSingleChContainer.getVisibility() == View.VISIBLE) refreshSingleChannelView();
        }

        @Override
        public void onChannelStateUpdated(IChannel channel) {
            Log.d(TAG, ">>> [EVENT] Channel State Updated");
            if (mSingleChContainer != null && mSingleChContainer.getVisibility() == View.VISIBLE) refreshSingleChannelView();
        }

        @Override
        public void onUserStateUpdated(IUser user) {
            if (getService() != null && getService().isConnected()) {
                try {
                    int selfSession = getService().HumlaSession().getSessionId();
                    if (user != null && user.getSession() == selfSession) configureInput();
                } catch (Exception e) {}
            }
            if (mSingleChContainer != null && mSingleChContainer.getVisibility() == View.VISIBLE) refreshSingleChannelView();
        }

        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (getService() == null || !getService().isConnected()) return;
            try {
                int selfSession = getService().HumlaSession().getSessionId();
                if (user != null && user.getSession() == selfSession && mTalkButton != null) {
                    mTalkButton.setActivated(isTalkingState(user.getTalkState()));
                }
                updateStageHeader(user);
                if (mSingleUserAdapter != null && mRvUserList != null) {
                    mSingleUserAdapter.updateUserState(user, mRvUserList);
                }
            } catch (Exception e) { Log.d(TAG, "Err talk state: " + e); }
        }

        @Override
        public void onVoiceTargetChanged(VoiceTargetMode mode) { configureTargetPanel(); }
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
            mTabStrip.setTextColor(a.getColor(1, -1));
            mTabStrip.setTabIndicatorColor(a.getColor(1, -1));
            mTabStrip.setBackgroundColor(a.getColor(0, -1));
            mTabStrip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            a.recycle();
        }

        mTalkView = view.findViewById(R.id.pushtotalk_view);
        mTalkButton = view.findViewById(R.id.pushtotalk);
        mTalkButton.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: v.setPressed(true); if(getService()!=null) getService().onTalkKeyDown(); break;
                case MotionEvent.ACTION_UP: v.setPressed(false); if(getService()!=null) getService().onTalkKeyUp(); break;
                case MotionEvent.ACTION_CANCEL: v.setPressed(false); if(getService()!=null) getService().onTalkKeyCancel(); break;
            }
            return true;
        });

        mTargetPanel = view.findViewById(R.id.target_panel);
        mTargetPanelCancel = view.findViewById(R.id.target_panel_cancel);
        mTargetPanelCancel.setOnClickListener(v -> {
            if (getService() == null || !getService().isConnected()) return;
            try {
                IHumlaSession s = getService().HumlaSession();
                if (s.getVoiceTargetMode() == VoiceTargetMode.WHISPER) {
                    byte t = s.getVoiceTargetId(); s.setVoiceTargetId((byte)0); s.unregisterWhisperTarget(t);
                }
            } catch (Exception e) {}
        });
        mTargetPanelText = view.findViewById(R.id.target_panel_warning);

        // === SETUP SINGLE CHANNEL VIEW CONTAINER ===
        mSingleChContainer = view.findViewById(R.id.single_ch_container);
        mRvUserList = view.findViewById(R.id.rv_user_list);
        
        if (mRvUserList != null) {
            mRvUserList.setItemAnimator(null); 
            mRvUserList.setLayoutManager(new LinearLayoutManager(requireContext()));
            mSingleUserAdapter = new SingleChannelUserAdapter(requireContext());
            mSingleUserAdapter.setOnUserClickListener(new SingleChannelUserAdapter.OnUserClickListener() {
                @Override public void onUserClick(IUser user) {}
                @Override public void onMoreClick(IUser user, View anchor) {
                    if (getService() instanceof MumlaService) {
                        new UserMenu(requireContext(), user, (MumlaService)getService(), getChildFragmentManager(), null).showPopup(anchor);
                    }
                }
            });
            mRvUserList.setAdapter(mSingleUserAdapter);
            
            mHeaderSingleCh = inflater.inflate(R.layout.header_single_channel, mRvUserList, false);
            
            mTvChannelId = mHeaderSingleCh.findViewById(R.id.tv_channel_id);
            mTvChannelName = mHeaderSingleCh.findViewById(R.id.tv_channel_name);
            mTvSpeakerName = mHeaderSingleCh.findViewById(R.id.tv_speaker_name);
            mImgMainAvatar = mHeaderSingleCh.findViewById(R.id.img_main_avatar);
            mTvChannelDesc = mHeaderSingleCh.findViewById(R.id.tv_channel_desc);
            mTvAnnouncementOverlay = mHeaderSingleCh.findViewById(R.id.tv_announcement_overlay);
            
            // ✅ INISIALISASI SMART ADMIN INPUT
            mAdminInputContainer = mHeaderSingleCh.findViewById(R.id.admin_input_container);
            mEtAdminAnnounce = mHeaderSingleCh.findViewById(R.id.et_admin_announce);
            mBtnSendAnnounce = mHeaderSingleCh.findViewById(R.id.btn_send_announce);

            // HANDLE KIRIM PENGUMUMAN LANGSUNG (DENGAN AUTO-HIDE FIX)
            mBtnSendAnnounce.setOnClickListener(v -> {
                String text = mEtAdminAnnounce.getText().toString().trim();
                if (!text.isEmpty() && getService() != null && getService().isConnected()) {
                    try {
                        IHumlaSession session = getService().HumlaSession();
                        IChannel currentCh = session.getSessionChannel();
                        
                        if (currentCh != null) {
                            // 1. UPDATE OVERLAY LANGSUNG
                            notifyAnnouncement(currentCh.getId(), text, "Admin");
                            
                            // 2. SIMPAN KE CACHE PERMANEN
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                            prefs.edit().putString("announcement_ch_" + currentCh.getId(), text).apply();
                            
                            // 3. KOSONGKAN INPUT & FEEDBACK
                            mEtAdminAnnounce.setText("");
                            Toast.makeText(requireContext(), "Pengumuman diperbarui!", Toast.LENGTH_SHORT).show();

                            // ✅ FIX KRUSIAL: SEMBUNYIKAN KOTAK INPUT & KEYBOARD SETELAH KIRIM
                            mAdminInputContainer.setVisibility(View.GONE);
                            
                            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null && mEtAdminAnnounce.getWindowToken() != null) {
                                imm.hideSoftInputFromWindow(mEtAdminAnnounce.getWindowToken(), 0);
                            }
                        }
                    } catch (Exception e) { Log.e(TAG, "Send announce failed", e); }
                }
            });

            ViewGroup stageContainer = view.findViewById(R.id.header_stage_container);
            if (stageContainer != null && mHeaderSingleCh != null) {
                stageContainer.addView(mHeaderSingleCh);
            }
        }

        // === SETUP BOTTOM BAR 5-TOMBOL ===
        mBottomControlBar = inflater.inflate(R.layout.bottom_control_bar, container, false);
        mBtnGlobalSet = mBottomControlBar.findViewById(R.id.btn_global_set);
        mBtnAudioMenu = mBottomControlBar.findViewById(R.id.btn_audio_menu);
        mPttContainer = mBottomControlBar.findViewById(R.id.ptt_container);
        mBtnListCh = mBottomControlBar.findViewById(R.id.btn_list_ch);
        mBtnSessionMenu = mBottomControlBar.findViewById(R.id.btn_session_menu);

        if (mTalkView != null && mTalkView.getParent() != null) ((ViewGroup)mTalkView.getParent()).removeView(mTalkView);
        if (mPttContainer != null && mTalkView != null) mPttContainer.addView(mTalkView);

        if (view instanceof ViewGroup) {
            ((ViewGroup) view).addView(mBottomControlBar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        }

        mBtnGlobalSet.setOnClickListener(v -> { if(getActivity() instanceof MumlaActivity) ((MumlaActivity)getActivity()).openNavigationDrawer(); });
        mBtnAudioMenu.setOnClickListener(v -> showAudioBottomSheet());
        mBtnListCh.setOnClickListener(v -> showChannelListDialog());
        mBtnSessionMenu.setOnClickListener(v -> showSessionMenu());

        configureInput();
        return view;
    }

    // === LOGIC UPDATE STAGE HEADER ===
    private void updateStageHeader(IUser talkingUser) {
        if (mTvSpeakerName == null || mImgMainAvatar == null) return;
        boolean isTalking = talkingUser != null && isTalkingState(talkingUser.getTalkState());
        
        if (isTalking) {
            mTvSpeakerName.setText(talkingUser.getName());
            mTvSpeakerName.setVisibility(View.VISIBLE);
            Drawable userAvatar = mSingleUserAdapter != null ? mSingleUserAdapter.getTalkStateDrawable(talkingUser) : null;
            if (userAvatar != null) mImgMainAvatar.setImageDrawable(userAvatar);
        } else {
            mTvSpeakerName.setText("");
            mTvSpeakerName.setVisibility(View.INVISIBLE);
            mImgMainAvatar.setImageResource(R.drawable.ic_launcher_foreground);
        }
    }

    // === METODE REFRESH SINGLE CH VIEW ===
    private void refreshSingleChannelView() {
        if (getService() == null || !getService().isConnected() || mSingleUserAdapter == null) return;
        
        try {
            IHumlaSession session = getService().HumlaSession();
            IChannel currentChannel = session.getSessionChannel();
            if (currentChannel == null) return;
            
            IChannel freshChannel = session.getChannel(currentChannel.getId());
            if (freshChannel != null) currentChannel = freshChannel;

            if (mTvChannelId != null) mTvChannelId.setText("CH " + currentChannel.getId());
            if (mTvChannelName != null) mTvChannelName.setText(currentChannel.getName());
            if (mTvChannelDesc != null) {
                String desc = currentChannel.getDescription();
                mTvChannelDesc.setText(desc != null ? desc : "");
                mTvChannelDesc.setVisibility(desc != null && !desc.isEmpty() ? View.VISIBLE : View.GONE);
            }
            
            updateStageHeader(null);
            loadCachedAnnouncement(currentChannel);
            
            // CEK PERMISSION ADMIN OTOMATIS SETIAP REFRESH (UNTUK MENAMPILKAN INPUT BOX)
            checkAndShowAdminInput(currentChannel);

            List<? extends IUser> rawUsers = currentChannel.getUsers();
            List<IUser> users = rawUsers != null ? new ArrayList<>(rawUsers) : new ArrayList<>();
            Log.d(TAG, ">>> Refreshing list with FRESH data. Count: " + users.size());
            mSingleUserAdapter.setUsers(users, currentChannel);
            
        } catch (Exception e) { Log.e(TAG, "Err refresh", e); }
    }

    // ✅ LOGIC DETEKSI ADMIN & TAMPILKAN INPUT CERAH
    private void checkAndShowAdminInput(IChannel channel) {
        if (mAdminInputContainer == null || channel == null) return;
        
        try {
            IHumlaSession session = getService().HumlaSession();
            int perms = session.getPermissions();
            
            // Definisi Admin: Punya hak Kick ATAU Ban ATAU Register
            final int ADMIN_FLAGS = Permissions.Kick | Permissions.Ban | Permissions.Register;
            
            if ((perms & ADMIN_FLAGS) != 0) {
                mAdminInputContainer.setVisibility(View.VISIBLE);
            } else {
                mAdminInputContainer.setVisibility(View.GONE);
            }
        } catch (Exception e) { 
            mAdminInputContainer.setVisibility(View.GONE); 
        }
    }

    // === METODE BRIDGE ANNOUNCEMENT ===
    public void notifyAnnouncement(int channelId, String message, String senderName) {
        if (mTvAnnouncementOverlay == null) return;
        try {
            IHumlaSession session = getService().HumlaSession();
            IChannel currentCh = session.getSessionChannel();
            if (currentCh != null && currentCh.getId() == channelId) {
                mTvAnnouncementOverlay.setText(message);
                mTvAnnouncementOverlay.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) { Log.e(TAG, "Notify announcement failed", e); }
    }

    // === LOAD CACHE ANNOUNCEMENT (PUBLIK - TANPA CEK PERMISSION) ===
    private void loadCachedAnnouncement(IChannel channel) {
        if (channel == null || mTvAnnouncementOverlay == null) return;
        
        // LANGSUNG LOAD DARI CACHE UNTUK SEMUA USER (PUBLIK)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String cached = prefs.getString("announcement_ch_" + channel.getId(), null);
        
        if (cached != null && !cached.isEmpty()) {
            mTvAnnouncementOverlay.setText(cached);
            mTvAnnouncementOverlay.setVisibility(View.VISIBLE);
        } else {
            mTvAnnouncementOverlay.setVisibility(View.GONE);
        }
    }

    // === METODE LIST CHANNEL DIALOG ===
    private void showChannelListDialog() {
        if (getService() == null || !getService().isConnected()) {
            Toast.makeText(requireContext(), "Belum connect", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            IHumlaSession session = getService().HumlaSession();
            IChannel root = session.getRootChannel();
            if (root == null) return;

            List<IChannel> allChannels = new ArrayList<>();
            collectAllChannels(root, allChannels);
            if (allChannels.isEmpty()) return;

            String[] names = new String[allChannels.size()];
            int[] ids = new int[allChannels.size()];
            for (int i=0; i<allChannels.size(); i++) {
                IChannel ch = allChannels.get(i);
                int cnt = ch.getUsers() != null ? ch.getUsers().size() : 0;
                names[i] = ch.getName() + " (" + cnt + ")";
                ids[i] = ch.getId();
            }

            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pilih Channel")
                .setItems(names, (d, w) -> {
                    session.joinChannel(ids[w]);
                    Toast.makeText(requireContext(), "Join: " + allChannels.get(w).getName(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null).show();
        } catch (Exception e) { Log.e(TAG, "Err list ch", e); }
    }

    private void collectAllChannels(IChannel p, List<IChannel> r) {
        if (p == null) return;
        r.add(p);
        List<? extends IChannel> subs = p.getSubchannels();
        if (subs != null) for (IChannel s : subs) collectAllChannels(s, r);
    }

    // === AUDIO BOTTOM SHEET ===
    private void showAudioBottomSheet() {
        if (getService() == null || !getService().isConnected() || !isAdded()) return;
        BottomSheetDialog bs = new BottomSheetDialog(requireContext());
        View sv = getLayoutInflater().inflate(R.layout.bottom_sheet_audio, null);
        bs.setContentView(sv);

        SwitchCompat swMute = sv.findViewById(R.id.switch_mute);
        SwitchCompat swDeafen = sv.findViewById(R.id.switch_deafen);
        try {
            IUser self = getService().HumlaSession().getSessionUser();
            if (self != null) { swMute.setChecked(self.isSelfMuted()||self.isMuted()); swDeafen.setChecked(self.isDeafened()); }
        } catch (Exception e) {}

        swMute.setOnCheckedChangeListener((b, c) -> {
            try { 
                boolean d=false; IUser s=getService().HumlaSession().getSessionUser(); if(s!=null)d=s.isDeafened();
                getService().HumlaSession().setSelfMuteDeafState(c, d); 
            } catch(Exception e){}
        });
        swDeafen.setOnCheckedChangeListener((b, c) -> {
            try {
                boolean m=false; IUser s=getService().HumlaSession().getSessionUser(); if(s!=null)m=s.isSelfMuted()||s.isMuted();
                getService().HumlaSession().setSelfMuteDeafState(m, c);
            } catch(Exception e){}
        });

        RadioGroup rg = sv.findViewById(R.id.radio_input_mode);
        Settings st = Settings.getInstance(requireContext());
        String cur = st.getInputMethod(); boolean stk = st.isPushToTalkToggle(); boolean bt = st.isBluetoothHeadset();
        
        if(cur.equals(Settings.ARRAY_INPUT_METHOD_VOICE)) sv.findViewById(R.id.radio_vox).performClick();
        else if(cur.equals(Settings.ARRAY_INPUT_METHOD_CONTINUOUS)) sv.findViewById(R.id.radio_continuous).performClick();
        else if(cur.equals(Settings.ARRAY_INPUT_METHOD_PTT)) {
            if(bt) sv.findViewById(R.id.radio_bluetooth).performClick();
            else if(stk) sv.findViewById(R.id.radio_ptt_sticky).performClick();
            else sv.findViewById(R.id.radio_ptt).performClick();
        }

        java.util.function.Consumer<Boolean> saveTgl = (v) -> android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()).edit().putBoolean("togglePtt", v).apply();

        rg.setOnCheckedChangeListener((g, id) -> {
            if(id==R.id.radio_vox) st.setInputMethod(Settings.ARRAY_INPUT_METHOD_VOICE);
            else if(id==R.id.radio_continuous) st.setInputMethod(Settings.ARRAY_INPUT_METHOD_CONTINUOUS);
            else if(id==R.id.radio_ptt) { st.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT); saveTgl.accept(false); st.setBluetoothHeadset(false); }
            else if(id==R.id.radio_ptt_sticky) { st.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT); saveTgl.accept(true); st.setBluetoothHeadset(false); }
            else if(id==R.id.radio_headset) { st.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT); saveTgl.accept(false); st.setBluetoothHeadset(false); Toast.makeText(requireContext(),"Siapkan tombol headset",Toast.LENGTH_SHORT).show(); }
            else if(id==R.id.radio_bluetooth) { st.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT); saveTgl.accept(true); st.setBluetoothHeadset(true); Toast.makeText(requireContext(),"BT PTT Aktif",Toast.LENGTH_SHORT).show(); }
            configureInput(); bs.dismiss();
        });
        bs.show();
    }

    // === SESSION MENU ===
    private void showSessionMenu() {
        if (!isAdded()) return;
        BottomSheetDialog bs = new BottomSheetDialog(requireContext());
        View sv = getLayoutInflater().inflate(R.layout.bottom_sheet_session_menu, null);
        bs.setContentView(sv);

        sv.findViewById(R.id.btn_toggle_overlay).setOnClickListener(v -> {
            if(getService() instanceof MumlaService) {
                MumlaService ms=(MumlaService)getService(); ms.onOverlayToggled();
                Toast.makeText(requireContext(), ms.isOverlayShown()?"Overlay ON":"Overlay OFF", Toast.LENGTH_SHORT).show();
            } bs.dismiss();
        });

        sv.findViewById(R.id.btn_refresh_channel).setOnClickListener(v -> {
            if(getService() instanceof HumlaService && ((HumlaService)getService()).isConnected()) {
                try {
                    HumlaService service = (HumlaService) getService();
                    int currentChId = service.getSessionChannel().getId();
                    service.requestPermissions(currentChId);
                    refreshSingleChannelView();
                    Toast.makeText(requireContext(), "Memuat ulang channel...", Toast.LENGTH_SHORT).show();
                } catch(Exception e) {
                    Log.e(TAG, "Error refresh channel", e);
                    Toast.makeText(requireContext(), "Gagal memuat ulang", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireContext(), "Belum terhubung", Toast.LENGTH_SHORT).show();
            }
            bs.dismiss();
        });

        sv.findViewById(R.id.btn_disconnect).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Disconnect?").setPositiveButton("Ya",(d,w)->{
                if(getService() instanceof MumlaService) ((MumlaService)getService()).onDisconnect();
                bs.dismiss();
            }).setNegativeButton("Batal",null).show();
        });
        bs.show();
    }

    // ✅ FIX KRUSIAL: PAKSA VIEWPAGER AKTIF & MATIKAN ANIMASI GESER
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);
        
        if (mViewPager != null) {
            mViewPager.setAdapter(new ChannelFragmentPagerAdapter(getChildFragmentManager()));
            mViewPager.setPageTransformer(false, null); 
        }
    }

    @Override public void onCreateOptionsMenu(Menu m, MenuInflater i) { i.inflate(R.menu.channel_menu, m); }
    @Override public void onPrepareOptionsMenu(Menu m) {
        MenuItem bi=m.findItem(R.id.menu_bluetooth_headset);
        if(bi!=null && getActivity()!=null) { bi.setVisible(getService()!=null&&getService().isConnected()); bi.setChecked(Settings.getInstance(getActivity()).isBluetoothHeadset()); }
    }
    @Override public boolean onOptionsItemSelected(MenuItem i) {
        if(i.getItemId()==R.id.menu_bluetooth_headset && getActivity() instanceof MumlaActivity) { ((MumlaActivity)getActivity()).setBluetoothHeadset(!Settings.getInstance(getActivity()).isBluetoothHeadset()); return true; }
        Settings s=Settings.getInstance(getActivity());
        if(i.getItemId()==R.id.menu_input_voice) s.setInputMethod(Settings.ARRAY_INPUT_METHOD_VOICE);
        else if(i.getItemId()==R.id.menu_input_ptt) s.setInputMethod(Settings.ARRAY_INPUT_METHOD_PTT);
        else if(i.getItemId()==R.id.menu_input_continuous) s.setInputMethod(Settings.ARRAY_INPUT_METHOD_CONTINUOUS);
        return super.onOptionsItemSelected(i);
    }

    @Override public void onPause() { super.onPause(); if(mTalkButton!=null)mTalkButton.setPressed(false); if(getService()!=null)getService().onTalkKeyCancel(); }

    @Override public void onDestroyView() {
        super.onDestroyView();
        if(mTalkButton!=null){mTalkButton.setActivated(false);mTalkButton.setPressed(false);}
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
        mViewPager=null; mTabStrip=null; mTalkButton=null; mTalkView=null; mTargetPanel=null; mTargetPanelCancel=null; mTargetPanelText=null;
        mSingleChContainer=null; mRvUserList=null; mSingleUserAdapter=null; mHeaderSingleCh=null;
        mTvChannelId=null; mTvChannelName=null; mTvSpeakerName=null; mImgMainAvatar=null; mTvChannelDesc=null;
        mTvAnnouncementOverlay=null; 
        mAdminInputContainer=null; mEtAdminAnnounce=null; mBtnSendAnnounce=null; // ✅ CLEANUP INPUT ADMIN
        mBottomControlBar=null; mBtnGlobalSet=null; mBtnAudioMenu=null; mBtnListCh=null; mBtnSessionMenu=null; mPttContainer=null;
    }

    @Override public IHumlaObserver getServiceObserver() { return mObserver; }

    @Override
    public void onServiceBound(IHumlaService service) {
        super.onServiceBound(service);
        if (service.getConnectionState() == HumlaService.ConnectionState.CONNECTED) {
            configureTargetPanel();
            configureInput();
            if (mSingleChContainer != null) {
                mSingleChContainer.setVisibility(View.VISIBLE);
                refreshSingleChannelView();
            }
        } else {
            if (mSingleChContainer != null) mSingleChContainer.setVisibility(View.GONE);
        }
    }

    private void configureTargetPanel() {
        if(!isAdded()||getActivity()==null||mTargetPanel==null||mTargetPanelText==null) return;
        if(getService()==null||!getService().isConnected()) return;
        try {
            IHumlaSession s=getService().HumlaSession(); VoiceTargetMode m=s.getVoiceTargetMode();
            WhisperTarget t=m==VoiceTargetMode.WHISPER?s.getWhisperTarget():null;
            if(t!=null){mTargetPanel.setVisibility(View.VISIBLE);mTargetPanelText.setText(getString(R.string.shout_target,t.getName()));}
            else mTargetPanel.setVisibility(View.GONE);
        } catch(Exception e){}
    }

    private boolean isShowingPinnedChannels() { return getArguments()!=null && getArguments().getBoolean("pinned"); }
    static int calculateButtonHeightPx(int h, android.util.DisplayMetrics m) {
        if(h<=0||m==null) return h; float p=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,h,m);
        return Math.max(1, Math.round(p>0?p:h*m.density));
    }
    static boolean isTalkingState(TalkState s) { return s!=null && (s==TalkState.TALKING||s==TalkState.SHOUTING||s==TalkState.WHISPERING); }

    private void configureInput() {
        if(!isAdded()||getActivity()==null||mTalkButton==null) return;
        Settings s=Settings.getInstance(getActivity());
        int hp=s.getPTTButtonHeight(); int px=calculateButtonHeightPx(hp,getResources()!=null?getResources().getDisplayMetrics():null);
        ViewGroup.LayoutParams p=mTalkButton.getLayoutParams(); if(p!=null){p.height=px;mTalkButton.setLayoutParams(p);}
        boolean muted=false;
        if(getService()!=null&&getService().isConnected()){
            try { IHumlaSession ss=getService().HumlaSession(); mTalkButton.setActivated(ss.isTalking()); IUser sl=ss.getSessionUser(); muted=sl==null||sl.isMuted()||sl.isSuppressed()||sl.isSelfMuted(); }
            catch(Exception e){}
        } else mTalkButton.setActivated(false);
        boolean show=!muted&&s.isPushToTalkButtonShown()&&s.getInputMethod().equals(Settings.ARRAY_INPUT_METHOD_PTT);
        setTalkButtonHidden(!show);
    }

    private void setTalkButtonHidden(boolean h) {
        if(mTalkView==null) return; mTalkView.setVisibility(h?View.GONE:View.VISIBLE);
        if(h&&mTalkButton!=null){mTalkButton.setActivated(false);mTalkButton.setPressed(false);}
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences s, String k) {
        if(Settings.PREF_INPUT_METHOD.equals(k)||Settings.PREF_PUSH_BUTTON_HIDE_KEY.equals(k)||Settings.PREF_PTT_BUTTON_HEIGHT.equals(k)) configureInput();
        if(Settings.PREF_BLUETOOTH_HEADSET.equals(k)&&isAdded()&&getActivity() instanceof MumlaActivity) ((MumlaActivity)getActivity()).supportInvalidateOptionsMenu();
    }

    @Override public ChatTarget getChatTarget() { return mChatTarget; }
    @Override public void setChatTarget(ChatTarget t) { mChatTarget=t; for(OnChatTargetSelectedListener l:mChatTargetListeners) l.onChatTargetSelected(t); }
    @Override public void registerChatTargetListener(OnChatTargetSelectedListener l) { mChatTargetListeners.add(l); }
    @Override public void unregisterChatTargetListener(OnChatTargetSelectedListener l) { mChatTargetListeners.remove(l); }

    private class ChannelFragmentPagerAdapter extends FragmentPagerAdapter {
        public ChannelFragmentPagerAdapter(FragmentManager fm) { super(fm); }
        @Override public Fragment getItem(int i) {
            Fragment f; Bundle a=new Bundle();
            if(i==0){f=new ChannelListFragment();a.putBoolean("pinned",isShowingPinnedChannels());} else f=new ChannelChatFragment();
            f.setArguments(a); return f;
        }
        @Override public CharSequence getPageTitle(int p) { return p==0?getString(R.string.channel).toUpperCase():getString(R.string.chat).toUpperCase(); }
        @Override public int getCount() { return 2; }
    }
}