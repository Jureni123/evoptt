/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified for Mumla OLED Single Channel View - Perfect Auto Sorting + Safe Hybrid Avatar
 */
package se.lublin.mumla.channel;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.TalkState;
import se.lublin.mumla.R;
import se.lublin.mumla.app.MumlaActivity;
import se.lublin.mumla.drawable.CircleDrawable;

public class SingleChannelUserAdapter extends RecyclerView.Adapter<SingleChannelUserAdapter.ViewHolder> {

    private final Context mContext;
    private final List<IUser> mUsers = new ArrayList<>();
    private OnUserClickListener mListener;
    
    // Simpan channel saat ini untuk cek badge parent
    private IChannel mCurrentChannel;
    
    // ✅ KONSTANTA HYBRID AVATAR SYSTEM
    private static final String PREF_MY_GIF_PATH = "my_gif_path";
    private static final String PREF_AVATAR_PREFIX = "avatar_session_";

    public interface OnUserClickListener {
        void onUserClick(IUser user);
        void onMoreClick(IUser user, View anchorView);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatarState;
        TextView tvUsername;
        TextView tvChannelBadge; 
        TextView tvLocationComment;
        ImageView imgMoreOptions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatarState = itemView.findViewById(R.id.img_avatar_state);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvChannelBadge = itemView.findViewById(R.id.tv_channel_badge);
            tvLocationComment = itemView.findViewById(R.id.tv_location_comment);
            imgMoreOptions = itemView.findViewById(R.id.img_more_options);
        }
    }

    public SingleChannelUserAdapter(Context context) {
        mContext = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(mContext).inflate(R.layout.item_user_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IUser user = mUsers.get(position);
        if (user == null) return;

        // 1. Set Nama User
        holder.tvUsername.setText(user.getName());
        
        // 2. Set Badge Channel (Fitur Parent CH)
        try {
            IChannel userChan = user.getChannel();
            if (userChan != null && mCurrentChannel != null && userChan.getId() != mCurrentChannel.getId()) {
                holder.tvChannelBadge.setText("CH " + userChan.getId());
                holder.tvChannelBadge.setVisibility(View.VISIBLE);
            } else {
                holder.tvChannelBadge.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            holder.tvChannelBadge.setVisibility(View.GONE);
        }

        // 3. Set Lokasi/Comment
        String comment = user.getComment();
        if (comment != null && !comment.isEmpty() && !comment.equalsIgnoreCase("null")) {
            holder.tvLocationComment.setText(comment);
            holder.tvLocationComment.setVisibility(View.VISIBLE);
        } else {
            holder.tvLocationComment.setVisibility(View.GONE);
        }

        // 4. ✅ LOGIKA BARU: LOAD AVATAR PER-USER DARI FILE LOKAL (ANTI-CLONING)
        boolean hasCustomAvatar = false;
        
        try {
            // Ambil session ID diri sendiri
            int mySessionId = -1;
            if (mContext instanceof MumlaActivity) {
                MumlaActivity activity = (MumlaActivity) mContext;
                if (activity.getService() != null && activity.getService().isConnected()) {
                    mySessionId = activity.getService().HumlaSession().getSessionId();
                }
            }

            // HANYA PROSES JIKA USER DI LIST INI ADALAH DIRI SENDIRI
            if (user.getSession() == mySessionId) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                String gifPath = prefs.getString(PREF_MY_GIF_PATH, null);
                
                if (gifPath != null && !gifPath.isEmpty()) {
                    File gifFile = new File(gifPath);
                    if (gifFile.exists()) {
                        hasCustomAvatar = true;
                        
                        // Load file asli pakai Glide (Support GIF Animasi + Circle Crop)
                        RequestOptions options = new RequestOptions()
                                .circleCrop()
                                .placeholder(R.drawable.outline_circle_talking_off)
                                .error(R.drawable.outline_circle_talking_off);
                                
                        Glide.with(mContext)
                             .asDrawable() // Wajib asDrawable biar GIF jalan
                             .load(gifFile)
                             .apply(options)
                             .into(holder.imgAvatarState);
                    }
                }
            }
        } catch (Exception e) {
            // Abaikan jika error, fallback ke bawah
            hasCustomAvatar = false;
        }

        // 5. ✅ FALLBACK: Jika tidak ada avatar custom → PAKAI TALK STATE DRAWABLE
        if (!hasCustomAvatar) {
            Drawable stateDrawable = getTalkStateDrawable(user);
            holder.imgAvatarState.setImageDrawable(stateDrawable);
        }

        // Setup Click Listener
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) mListener.onUserClick(user);
        });

        holder.imgMoreOptions.setOnClickListener(v -> {
            if (mListener != null) mListener.onMoreClick(user, v);
        });
    }

    @Override
    public int getItemCount() {
        return mUsers.size();
    }

    /**
     * Mengupdate daftar user & channel saat ini DENGAN AUTO-SORTING.
     */
    public void setUsers(List<IUser> users, IChannel currentChannel) {
        mUsers.clear();
        mCurrentChannel = currentChannel; 
        
        if (users != null) {
            mUsers.addAll(users);
            
            // === LOGIC SORTING OTOMATIS ===
            Collections.sort(mUsers, (user1, user2) -> {
                boolean u1Talking = isTalkingState(user1.getTalkState());
                boolean u2Talking = isTalkingState(user2.getTalkState());
                
                if (u1Talking && !u2Talking) return -1;
                if (!u1Talking && u2Talking) return 1;
                
                return user1.getName().compareToIgnoreCase(user2.getName());
            });
        }
        
        notifyDataSetChanged();
        
        // ✅ TAMBAHKAN INI: Paksa cek ulang avatar diri sendiri setelah list di-refresh
        checkAndRefreshMyAvatar(); 
    }

    // ✅ METHOD BARU: Cek apakah ada avatar baru di storage dan refresh barisnya
    private void checkAndRefreshMyAvatar() {
        try {
            if (mContext instanceof MumlaActivity) {
                MumlaActivity activity = (MumlaActivity) mContext;
                if (activity.getService() != null && activity.getService().isConnected()) {
                    int mySessionId = activity.getService().HumlaSession().getSessionId();
                    
                    // Cari posisi diri sendiri di list
                    for (int i = 0; i < mUsers.size(); i++) {
                        if (mUsers.get(i).getSession() == mySessionId) {
                            notifyItemChanged(i); // Trigger onBindViewHolder ulang untuk baris ini
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) { /* Abaikan */ }
    }

    // Overload method lama agar kompatibel
    public void setUsers(List<IUser> users) {
        setUsers(users, null);
    }

    /**
     * Update state satu user secara real-time DENGAN RE-SORTING.
     */
    public void updateUserState(IUser user, RecyclerView recyclerView) {
        if (user == null) return;
        
        int oldIndex = mUsers.indexOf(user);
        if (oldIndex < 0) return;

        Collections.sort(mUsers, (u1, u2) -> {
            boolean t1 = isTalkingState(u1.getTalkState());
            boolean t2 = isTalkingState(u2.getTalkState());
            
            if (t1 && !t2) return -1;
            if (!t1 && t2) return 1;
            
            return u1.getName().compareToIgnoreCase(u2.getName());
        });

        int newIndex = mUsers.indexOf(user);
        
        if (oldIndex != newIndex) {
            notifyItemMoved(oldIndex, newIndex);
            notifyItemChanged(oldIndex); 
            notifyItemChanged(newIndex);
        } else {
            notifyItemChanged(newIndex);
        }
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        mListener = listener;
    }

    // === HELPER METHOD CEK TALKING STATE ===
    private boolean isTalkingState(TalkState state) {
        return state == TalkState.TALKING || 
               state == TalkState.SHOUTING || 
               state == TalkState.WHISPERING;
    }

    // === LOGIKA DRAWABLE STATUS AUDIO (FALLBACK JIKA TIDAK ADA AVATAR CUSTOM) ===
    public Drawable getTalkStateDrawable(IUser user) {
        if (user == null) {
            return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_talking_off);
        }
        if (user.isSelfDeafened()) {
            return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_deafened);
        } else if (user.isDeafened()) {
            return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_server_deafened);
        } else if (user.isSelfMuted()) {
            return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_muted);
        } else if (user.isMuted()) {
            return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_server_muted);
        } else if (user.isSuppressed()) {
            return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_suppressed);
        } else if (isTalkingState(user.getTalkState())) {
            return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_talking_on);
        }
        return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_talking_off);
    }

    /**
     * ✅ HELPER BARU: Ambil Avatar Bitmap untuk User (Dipakai oleh ChannelFragment untuk Header Stage)
     * Menggunakan logika file lokal yang sama agar konsisten
     */
    public Bitmap getAvatarBitmapForUser(IUser user) {
        if (user == null) return null;
        
        try {
            if (mContext instanceof MumlaActivity) {
                MumlaActivity activity = (MumlaActivity) mContext;
                if (activity.getService() != null && activity.getService().isConnected()) {
                    int mySessionId = activity.getService().HumlaSession().getSessionId();
                    
                    if (user.getSession() == mySessionId) {
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
                        String gifPath = prefs.getString(PREF_MY_GIF_PATH, null);
                        
                        if (gifPath != null && !gifPath.isEmpty()) {
                            File gifFile = new File(gifPath);
                            if (gifFile.exists()) {
                                // Decode frame pertama untuk header stage
                                return Glide.with(mContext)
                                            .asBitmap()
                                            .load(gifFile)
                                            .submit(96, 96)
                                            .get();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback diam jika gagal load
        }
        return null;
    }
}