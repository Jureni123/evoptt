/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified for Mumla OLED Single Channel View - Perfect Auto Sorting + Dynamic Avatar Per-User
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
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

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
    
    // ✅ KONSTANTA STORAGE PER-USER
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

        // 4. ✅ LOGIKA BARU: LOAD AVATAR PER-USER DARI BASE64
        boolean hasCustomAvatar = false;
        
        try {
            int sessionId = user.getSession();
            Bitmap avatarBitmap = MumlaActivity.getAvatarForUser(mContext, sessionId);
            
            if (avatarBitmap != null) {
                hasCustomAvatar = true;
                
                // Load Bitmap ke ImageView dengan Circle Crop via Glide
                RequestOptions options = new RequestOptions()
                        .circleCrop()
                        .placeholder(R.drawable.outline_circle_talking_off)
                        .error(R.drawable.outline_circle_talking_off);
                        
                Glide.with(mContext)
                     .load(avatarBitmap)
                     .apply(options)
                     .into(holder.imgAvatarState);
            }
        } catch (Exception e) {
            hasCustomAvatar = false;
        }

        // 5. ✅ JIKA TIDAK ADA AVATAR CUSTOM → PAKAI TALK STATE DRAWABLE
        // Jika ADA avatar custom → tetap tampilkan avatar, tapi tambah indikator status
        if (!hasCustomAvatar) {
            Drawable stateDrawable = getTalkStateDrawable(user);
            holder.imgAvatarState.setImageDrawable(stateDrawable);
        } else {
            // Avatar custom sudah di-load oleh Glide di atas
            // Tambahkan indikator status audio sebagai overlay kecil (opsional)
            // Untuk sekarang, avatar custom tetap tampil meski user sedang bicara
            // Ini lebih baik daripada ditimpa total
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
     */
    public Bitmap getAvatarBitmapForUser(IUser user) {
        if (user == null) return null;
        return MumlaActivity.getAvatarForUser(mContext, user.getSession());
    }
}