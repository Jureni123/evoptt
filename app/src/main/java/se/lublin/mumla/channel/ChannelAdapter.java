/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified for Mumla OLED - Auto Sorting & Optimized Update
 */
package se.lublin.mumla.channel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
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

/**
 * Modern RecyclerView adapter with VIRTUAL SORTING layer.
 * Reads from IChannel but displays in sorted order (Talking > A-Z).
 */
public final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ViewHolder> {

    private final AvatarCache mAvatarCache = new AvatarCache();
    private final Context mContext;
    private IChannel mChannel;
    
    // VIRTUAL SORTED INDICES: Menyimpan urutan posisi user setelah di-sorting
    // Agar tidak perlu memodifikasi object IChannel asli dari server
    private final List<Integer> mSortedIndices = new ArrayList<>();

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView stateIcon;
        final TextView userName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            stateIcon = itemView.findViewById(R.id.user_row_state);
            userName = itemView.findViewById(R.id.user_row_name);
        }
    }

    public ChannelAdapter(Context context, IChannel channel) {
        mContext = context;
        setChannel(channel);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(mContext).inflate(R.layout.overlay_user_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (mChannel == null || mChannel.getUsers() == null) return;
        
        // AMBIL USER DARI INDEKS YANG SUDAH DI-SORTING
        int actualIndex = mSortedIndices.get(position);
        IUser user = mChannel.getUsers().get(actualIndex);
        
        if (user == null) return;

        holder.userName.setText(user.getName());
        holder.stateIcon.setImageDrawable(getTalkStateDrawable(user));
    }

    @Override
    public int getItemCount() {
        return mSortedIndices.size();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= mSortedIndices.size()) return RecyclerView.NO_ID;
        
        int actualIndex = mSortedIndices.get(position);
        if (mChannel != null && mChannel.getUsers() != null && actualIndex < mChannel.getUsers().size()) {
            IUser user = mChannel.getUsers().get(actualIndex);
            if (user != null) return user.getSession();
        }
        return RecyclerView.NO_ID;
    }

    /**
     * Set channel baru DAN lakukan sorting virtual sekaligus
     */
    public void setChannel(IChannel channel) {
        mChannel = channel;
        updateSorting(); // Sort saat data baru masuk
        notifyDataSetChanged();
    }

    /**
     * METHOD RAHASIA: Melakukan sorting pada daftar indeks virtual
     * Prioritas: 1. Talking/Shouting/Whispering -> 2. Nama A-Z
     */
    private void updateSorting() {
        mSortedIndices.clear();
        if (mChannel == null || mChannel.getUsers() == null) return;

        List<? extends IUser> users = mChannel.getUsers();
        for (int i = 0; i < users.size(); i++) {
            mSortedIndices.add(i);
        }

        Collections.sort(mSortedIndices, (idx1, idx2) -> {
            IUser u1 = users.get(idx1);
            IUser u2 = users.get(idx2);
            
            boolean t1 = isTalking(u1);
            boolean t2 = isTalking(u2);

            // Yang bicara selalu di atas
            if (t1 && !t2) return -1;
            if (!t1 && t2) return 1;

            // Jika status sama, urutkan nama A-Z
            String n1 = u1 != null ? u1.getName() : "";
            String n2 = u2 != null ? u2.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });
    }

    /**
     * Update state user secara real-time DENGAN RE-SORTING OTOMATIS
     */
    public void updateUserState(IUser user, RecyclerView view) {
        if (mChannel == null || user == null) return;

        // Cari posisi asli user di data server
        int originalIndex = mChannel.getUsers().indexOf(user);
        if (originalIndex < 0) return;

        // Cek apakah user sedang terlihat di layar
        ViewHolder vh = (view != null) ? (ViewHolder) view.findViewHolderForAdapterPosition(
                mSortedIndices.indexOf(originalIndex)) : null;

        if (vh != null) {
            // Update visual langsung tanpa animasi pindah (lebih smooth untuk talking state)
            vh.userName.setText(user.getName());
            Drawable newState = getTalkStateDrawable(user);
            Drawable current = vh.stateIcon.getDrawable();
            
            if (newState != null && current != null) {
                ConstantState cs = (current.getCurrent() != null) ? 
                        current.getCurrent().getConstantState() : null;
                if (cs == null || !cs.equals(newState.getConstantState())) {
                    vh.stateIcon.setImageDrawable(newState);
                }
            } else if (newState != null) {
                vh.stateIcon.setImageDrawable(newState);
            }
        }

        // PENTING: Lakukan re-sorting agar user yang baru bicara naik ke atas
        updateSorting();
        notifyDataSetChanged(); 
    }

    public void notifyUserChanged(IUser user) {
        if (mChannel == null || user == null) return;
        int originalIndex = mChannel.getUsers().indexOf(user);
        if (originalIndex >= 0) {
            updateSorting();
            notifyDataSetChanged();
        }
    }

    // Helper cek talking state
    private boolean isTalking(IUser user) {
        if (user == null) return false;
        TalkState s = user.getTalkState();
        return s == TalkState.TALKING || s == TalkState.SHOUTING || s == TalkState.WHISPERING;
    }

    Drawable getTalkStateDrawable(IUser user) {
        if (mContext == null || user == null) return null;
        
        if (user.isSelfDeafened()) return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_deafened);
        if (user.isDeafened()) return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_server_deafened);
        if (user.isSelfMuted()) return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_muted);
        if (user.isMuted()) return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_server_muted);
        if (user.isSuppressed()) return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_suppressed);
        
        if (isTalking(user)) return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_talking_on);

        Bitmap bitmap = mAvatarCache.get(user);
        if (bitmap != null) return new CircleDrawable(mContext.getResources(), bitmap);
        
        return AppCompatResources.getDrawable(mContext, R.drawable.outline_circle_talking_off);
    }

    public IChannel getChannel() { return mChannel; }
    public void removeUser(int session) { mAvatarCache.remove(session); }
    public void clearAvatarCache() { mAvatarCache.clear(); }

    @VisibleForTesting
    AvatarCache getAvatarCache() { return mAvatarCache; }
}