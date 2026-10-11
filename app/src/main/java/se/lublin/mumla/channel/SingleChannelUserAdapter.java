/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified for Mumla OLED Single Channel View
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
import java.util.List;

import se.lublin.humla.model.IUser;
import se.lublin.humla.model.TalkState;
import se.lublin.mumla.R;
import se.lublin.mumla.drawable.CircleDrawable;

public class SingleChannelUserAdapter extends RecyclerView.Adapter<SingleChannelUserAdapter.ViewHolder> {

    private final Context mContext;
    private final List<IUser> mUsers = new ArrayList<>();
    private final AvatarCache mAvatarCache = new AvatarCache();
    private OnUserClickListener mListener;

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

        // Set Nama
        holder.tvUsername.setText(user.getName());
        
        // Set Lokasi/Comment (Fallback ke string kosong jika null)
        String comment = user.getComment();
        holder.tvLocationComment.setText(comment != null ? comment : "");
        holder.tvLocationComment.setVisibility(comment != null && !comment.isEmpty() ? View.VISIBLE : View.GONE);

        // Set State Icon / Avatar
        holder.imgAvatarState.setImageDrawable(getTalkStateDrawable(user));

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
     * Mengupdate daftar user secara keseluruhan.
     * Panggil ini saat channel berubah atau user join/part.
     */
    public void setUsers(List<IUser> users) {
        mUsers.clear();
        if (users != null) {
            mUsers.addAll(users);
        }
        notifyDataSetChanged();
    }

    /**
     * Update state satu user secara real-time tanpa reload seluruh list.
     * Diambil dari ChannelListAdapter asli.
     */
    public void updateUserState(IUser user, RecyclerView recyclerView) {
        int index = mUsers.indexOf(user);
        if (index < 0) return;

        ViewHolder vh = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(index);
        if (vh != null) {
            Drawable newState = getTalkStateDrawable(user);
            Drawable current = vh.imgAvatarState.getDrawable();
            
            // Cek apakah drawable benar-benar berubah sebelum set ulang
            if (newState != null && current != null) {
                Drawable.ConstantState csNew = newState.getConstantState();
                Drawable.ConstantState csCurrent = current.getConstantState();
                if (csNew == null || !csNew.equals(csCurrent)) {
                    vh.imgAvatarState.setImageDrawable(newState);
                }
            } else if (newState != null) {
                vh.imgAvatarState.setImageDrawable(newState);
            }
        } else {
            notifyItemChanged(index);
        }
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        mListener = listener;
    }

    // === LOGIKA DRAWABLE DIAMBIL DARI CHANNELLISTADAPTER ASLI ===
    private Drawable getTalkStateDrawable(IUser user) {
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
        } else if (user.getTalkState() == TalkState.TALKING
                || user.getTalkState() == TalkState.SHOUTING
                || user.getTalkState() == TalkState.WHISPERING) {
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