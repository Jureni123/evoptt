/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified for Mumla OLED Single Channel View - Hello Live Style
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
        TextView tvLevelBadge;       // BARU: Untuk "HM c786"
        TextView tvParentChBadge;    // BARU: Untuk "CH 005"
        TextView tvLocationComment;
        ImageView imgTalkingIndicator; // BARU: Icon mikrofon hijau

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatarState = itemView.findViewById(R.id.img_avatar_state);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvLevelBadge = itemView.findViewById(R.id.tv_level_badge);
            tvParentChBadge = itemView.findViewById(R.id.tv_parent_ch_badge);
            tvLocationComment = itemView.findViewById(R.id.tv_location_comment);
            imgTalkingIndicator = itemView.findViewById(R.id.img_talking_indicator);
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
        
        // 2. Set Badge Level (Simulasi/Placeholder)
        // Karena IUser standar tidak punya field "level", kita bisa pakai Session ID atau Comment sebagai placeholder
        // Nanti kalau ada API khusus level, tinggal ganti di sini.
        String levelText = "HM " + Integer.toHexString(user.getSession()).substring(0, 4); 
        holder.tvLevelBadge.setText(levelText);
        holder.tvLevelBadge.setVisibility(View.VISIBLE);

        // 3. Set Badge Channel Parent (Fitur Hello Live!)
        // Cek apakah user berada di channel yang sama dengan current channel
        if (mCurrentChannel != null && user.getChannelId() != mCurrentChannel.getId()) {
            // Jika beda, tampilkan badge channel asalnya
            // Catatan: IUser biasanya tidak langsung punya nama channel parent, 
            // jadi kita butuh logic tambahan atau ambil dari session data jika tersedia.
            // Untuk sementara, kita tampilkan ID channel-nya saja.
            holder.tvParentChBadge.setText("CH " + user.getChannelId());
            holder.tvParentChBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvParentChBadge.setVisibility(View.GONE);
        }

        // 4. Set Lokasi/Comment (Handle "null" agar rapi)
        String comment = user.getComment();
        if (comment != null && !comment.isEmpty() && !comment.equals("null")) {
            holder.tvLocationComment.setText(comment);
            holder.tvLocationComment.setVisibility(View.VISIBLE);
        } else {
            holder.tvLocationComment.setVisibility(View.GONE);
        }

        // 5. Set State Icon / Avatar (Logic Lama Tetap Jalan)
        Drawable stateDrawable = getTalkStateDrawable(user);
        holder.imgAvatarState.setImageDrawable(stateDrawable);

        // 6. Indikator Talking Terpisah (Icon Mikrofon Hijau)
        boolean isTalking = (user.getTalkState() == TalkState.TALKING || 
                             user.getTalkState() == TalkState.SHOUTING || 
                             user.getTalkState() == TalkState.WHISPERING);
        holder.imgTalkingIndicator.setVisibility(isTalking ? View.VISIBLE : View.INVISIBLE);

        // Setup Click Listener
        holder.itemView.setOnClickListener(v -> {
            if (mListener != null) mListener.onUserClick(user);
        });
    }

    @Override
    public int getItemCount() {
        return mUsers.size();
    }

    /**
     * Mengupdate daftar user & channel saat ini.
     */
    public void setUsers(List<IUser> users, IChannel currentChannel) {
        mUsers.clear();
        mCurrentChannel = currentChannel; // Simpan referensi channel aktif
        if (users != null) {
            mUsers.addAll(users);
        }
        notifyDataSetChanged();
    }

    /**
     * Update state satu user secara real-time.
     */
    public void updateUserState(IUser user, RecyclerView recyclerView) {
        int index = mUsers.indexOf(user);
        if (index < 0) return;

        ViewHolder vh = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(index);
        if (vh != null) {
            // Update Avatar/State Icon
            Drawable newState = getTalkStateDrawable(user);
            vh.imgAvatarState.setImageDrawable(newState);
            
            // Update Indikator Talking
            boolean isTalking = (user.getTalkState() == TalkState.TALKING || 
                                 user.getTalkState() == TalkState.SHOUTING || 
                                 user.getTalkState() == TalkState.WHISPERING);
            vh.imgTalkingIndicator.setVisibility(isTalking ? View.VISIBLE : View.INVISIBLE);
        } else {
            notifyItemChanged(index);
        }
    }

    public void setOnUserClickListener(OnUserClickListener listener) {
        mListener = listener;
    }

    // === LOGIKA DRAWABLE (SAMA PERSIS DENGAN CHANNELLISTADAPTER) ===
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