/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified for Mumla OLED Single Channel View - Auto Sorting Version
 */
package se.lublin.mumla.channel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.TalkState;
import se.lublin.mumla.R;
import se.lublin.mumla.drawable.CircleDrawable;

public class SingleChannelUserAdapter extends RecyclerView.Adapter<SingleChannelUserAdapter.ViewHolder> {

    private final Context mContext;
    private final List<IUser> mUsers = new ArrayList<>();
    private final AvatarCache mAvatarCache = new AvatarCache();
    private OnUserClickListener mListener;
    
    // Simpan channel saat ini untuk cek badge parent
    private IChannel mCurrentChannel;

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

        // 4. Set State Icon / Avatar
        Drawable stateDrawable = getTalkStateDrawable(user);
        holder.imgAvatarState.setImageDrawable(stateDrawable);

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
            // Prioritas: 1. Sedang Bicara (Talking/Shouting/Whispering)
            //            2. Urutan Alfabetis (A-Z) jika status sama
            Collections.sort(mUsers, (user1, user2) -> {
                boolean u1Talking = isTalkingState(user1.getTalkState());
                boolean u2Talking = isTalkingState(user2.getTalkState());
                
                // Jika user1 bicara dan user2 tidak, user1 naik ke atas (-1)
                if (u1Talking && !u2Talking) return -1;
                // Jika user2 bicara dan user1 tidak, user2 naik ke atas (1)
                if (!u1Talking && u2Talking) return 1;
                
                // Jika sama-sama bicara atau sama-sama diam, urutkan nama A-Z
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
     * Update state satu user secara real-time.
     * CATATAN: Karena sorting mengubah posisi, update single item bisa tricky.
     * Untuk keamanan & konsistensi sorting, lebih baik refresh full list saat state berubah.
     */
    public void updateUserState(IUser user, RecyclerView recyclerView) {
        // Opsi Aman: Refresh seluruh list agar sorting tetap konsisten
        // Cari channel saat ini dari adapter atau pass ulang jika perlu
        // Untuk sementara, kita biarkan update visual saja tanpa re-sort di sini
        // Re-sort akan terjadi otomatis saat refreshSingleChannelView() dipanggil dari Fragment
        
        int index = mUsers.indexOf(user);
        if (index >= 0) {
            notifyItemChanged(index);
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

    // === LOGIKA DRAWABLE (SAMA PERSIS DENGAN CHANNELLISTADAPTER) ===
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
        } else {
            Bitmap bitmap = mAvatarCache.get(user);
            if (bitmap != null) {
                return new CircleDrawable(mContext.getResources(), bitmap);
            }
        }
        return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_talking_off);
    }
}