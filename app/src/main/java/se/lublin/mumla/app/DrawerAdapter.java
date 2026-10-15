/*
 * Copyright (C) 2014 Andrew Comminos
 * Modified for Mumla OLED - Added Profile Photo Menu Item
 */
package se.lublin.mumla.app;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import se.lublin.mumla.R;

/**
 * Adapter untuk Navigation Drawer Mumla OLED.
 * Menampilkan daftar menu termasuk opsi baru "Foto Profil".
 */
public class DrawerAdapter extends ArrayAdapter<DrawerAdapter.DrawerRow> {

    /**
     * Interface untuk mendapatkan status koneksi dari Activity utama.
     */
    public interface DrawerDataProvider {
        boolean isConnected();
        String getConnectedServerName();
    }

    // === KONSTANTA ID MENU DRAWER ===
    public static final int HEADER_CONNECTED_SERVER = 0;
    public static final int ITEM_SERVER = 1;
    public static final int ITEM_PINNED_CHANNELS = 2;
    public static final int ITEM_INFO = 3;
    
    // ✅ ID BARU UNTUK MENU FOTO PROFIL
    public static final int ITEM_PROFILE_PHOTO = 7; 
    
    public static final int ITEM_ACCESS_TOKENS = 4;
    public static final int HEADER_SERVERS = 5;
    public static final int ITEM_FAVOURITES = 6;
    public static final int HEADER_GENERAL = 9;
    public static final int ITEM_SETTINGS = 10;

    private static final int HEADER_TYPE = 0;
    private static final int ITEM_TYPE = 1;

    // === CLASS DATA ROW ===
    public static class DrawerRow {
        int id;
        String title;

        private DrawerRow(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public static class DrawerHeader extends DrawerRow {
        public DrawerHeader(int id, String title) {
            super(id, title);
        }
    }

    public static class DrawerItem extends DrawerRow {
        int icon;

        public DrawerItem(int id, String title, int icon) {
            super(id, title);
            this.icon = icon;
        }
    }

    private DrawerDataProvider mProvider;

    public DrawerAdapter(Context context, DrawerDataProvider provider) {
        super(context, 0);
        mProvider = provider;
        
        // --- GRUP: SATU UNTUK SEMUA ---
        add(new DrawerHeader(HEADER_CONNECTED_SERVER, context.getString(R.string.drawer_not_connected)));
        add(new DrawerItem(ITEM_SERVER, context.getString(R.string.drawer_server), R.drawable.ic_action_channels));
        add(new DrawerItem(ITEM_PINNED_CHANNELS, context.getString(R.string.drawer_pinned), R.drawable.ic_action_comment));
        add(new DrawerItem(ITEM_INFO, context.getString(R.string.information), R.drawable.ic_action_info_dark));
        
        // ✅ LANGKAH 1: TAMBAHKAN MENU FOTO PROFIL DI BAWAH INFORMATION
        // Gunakan icon ic_action_person atau ic_camera_alt jika ada. 
        // Jika belum ada, pakai ic_action_info_dark sementara biar build sukses.
        add(new DrawerItem(ITEM_PROFILE_PHOTO, "Foto Profil", R.drawable.ic_action_info_dark)); 
        
        add(new DrawerItem(ITEM_ACCESS_TOKENS, context.getString(R.string.drawer_tokens), R.drawable.ic_action_save));
        
        // --- GRUP: SERVERS ---
        add(new DrawerHeader(HEADER_SERVERS, context.getString(R.string.drawer_header_servers)));
        add(new DrawerItem(ITEM_FAVOURITES, context.getString(R.string.drawer_favorites), R.drawable.ic_action_favourite_on));
        
        // --- GRUP: GENERAL ---
        add(new DrawerHeader(HEADER_GENERAL, context.getString(R.string.general)));
        add(new DrawerItem(ITEM_SETTINGS, context.getString(R.string.action_settings), R.drawable.ic_action_settings));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        int viewType = getItemViewType(position);
        
        if(v == null) {
            if(viewType == HEADER_TYPE)
                v = LayoutInflater.from(getContext()).inflate(R.layout.list_drawer_header, parent, false);
            else if(viewType == ITEM_TYPE)
                v = LayoutInflater.from(getContext()).inflate(R.layout.list_drawer_item, parent, false);
        }

        if(viewType == HEADER_TYPE) {
            DrawerHeader header = (DrawerHeader) getItem(position);
            TextView title = v.findViewById(R.id.drawer_header_title);

            switch((int) getItemId(position)) {
                case HEADER_CONNECTED_SERVER:
                    if (mProvider.isConnected()) {
                        title.setText(mProvider.getConnectedServerName());
                    } else {
                        title.setText(header.title);
                    }
                    break;
                default:
                    title.setText(header.title);
                    break;
            }
        } else if(viewType == ITEM_TYPE) {
            DrawerItem item = (DrawerItem) getItem(position);
            TextView title = v.findViewById(R.id.drawer_item_title);
            ImageView icon = v.findViewById(R.id.drawer_item_icon);
            
            title.setText(item.title);
            icon.setImageResource(item.icon);

            boolean enabled = isEnabled(position);

            // Atur warna teks dan icon berdasarkan status enabled/disabled
            int textColor = title.getCurrentTextColor();
            textColor &= 0x00FFFFFF; 
            textColor |= enabled ? 0xFF000000 : 0x55000000; 
            
            title.setTextColor(textColor);
            icon.setColorFilter(textColor, PorterDuff.Mode.MULTIPLY);
        }

        return v;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    public DrawerRow getItemWithId(int id) {
        for(int x=0;x<getCount();x++) {
            DrawerRow row = getItem(x);
            if(row.id == id) return row;
        }
        return null;
    }

    @Override
    public boolean isEnabled(int position) {
        int viewType = getItemViewType(position);
        if(viewType == ITEM_TYPE) {
            switch ((int) getItemId(position)) {
                case ITEM_SERVER:
                case ITEM_INFO:
                case ITEM_ACCESS_TOKENS:
                case ITEM_PINNED_CHANNELS:
                case ITEM_PROFILE_PHOTO: // ✅ AKTIFKAN MENU FOTO PROFIL HANYA SAAT CONNECTED
                    return mProvider.isConnected();
                default:
                    return true;
            }
        }
        return false; 
    }

    @Override
    public int getItemViewType(int position) {
        Object item = getItem(position);
        if(item instanceof DrawerHeader)
            return HEADER_TYPE;
        else if(item instanceof DrawerItem)
            return ITEM_TYPE;
        return ITEM_TYPE;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }
}