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

package se.lublin.mumla.servers;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import java.util.List;

import se.lublin.humla.model.Server;
import se.lublin.mumla.R;
import se.lublin.mumla.db.DatabaseProvider;

/**
 * Displays a minimal, full-screen view of the favorite server for direct connection.
 * Optimized for Mumla OLED secure & immersive experience.
 */
public class FavouriteServerListFragment extends Fragment implements AdapterView.OnItemClickListener {

    private ServerConnectHandler mConnectHandler;
    private DatabaseProvider mDatabaseProvider;
    private GridView mServerGrid;
    private ServerAdapter<Server> mServerAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Kita nonaktifkan menu options agar Top Bar benar-benar bersih
        setHasOptionsMenu(false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        try {
            mConnectHandler = (ServerConnectHandler) context;
            mDatabaseProvider = (DatabaseProvider) context
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ServerConnectHandler and DatabaseProvider!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server_list, container, false);
        
        mServerGrid = view.findViewById(R.id.server_list_grid);
        mServerGrid.setOnItemClickListener(this);
        mServerGrid.setEmptyView(view.findViewById(R.id.server_list_grid_empty));

        // CATATAN: registerForContextMenu dihapus agar user tidak bisa long-press 
        // untuk edit/delete server (Demi keamanan & tampilan minimalis)
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        
        // 1. SEMBUNYIKAN ACTION BAR untuk efek Full Screen Imersif (seperti Hello Live)
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().hide();
            }
        }
        
        // 2. Update daftar server
        updateServers();
        }

    @Override
    public void onPause() {
        super.onPause();
        // Opsional: Munculkan kembali Action Bar saat pindah ke fragment lain
        // Biarkan MumlaActivity yang menangani ini agar lebih aman
    }

    public void updateServers() {
        List<Server> servers = getServers();
        // Listener dilempar sebagai 'this', tapi karena menu dihapus, fungsinya tidak akan terpanggil
        mServerAdapter = new FavouriteServerAdapter(getActivity(), servers, (FavouriteServerAdapter.FavouriteServerAdapterMenuListener) this);
        mServerGrid.setAdapter(mServerAdapter);
    }

    public List<Server> getServers() {
        return mDatabaseProvider.getDatabase().getServers();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Langsung connect saat card ditekan, simpel dan cepat
        Server selectedServer = mServerAdapter.getItem(position);
        if (selectedServer != null) {
            mConnectHandler.connectToServer(selectedServer);
        }
    }

    // Interface ini dipertahankan agar tidak error dengan FavouriteServerAdapter, 
    // tapi method-nya dibiarkan kosong karena kita sudah menghapus menu edit/delete/share.
    @Override
    public void editServer(Server server) { }

    @Override
    public void shareServer(Server server) { }

    @Override
    public void deleteServer(Server server) { }

    public interface ServerConnectHandler {
        void connectToServer(Server server);
    }
}