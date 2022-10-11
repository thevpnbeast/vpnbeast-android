package com.vpnbeast.android.fragment;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;
import com.vpnbeast.android.R;
import com.vpnbeast.android.activity.MainActivity;
import com.vpnbeast.android.model.entity.Server;
import com.vpnbeast.android.model.enums.AppConstants;
import com.vpnbeast.android.util.PersistenceUtil;
import java.util.List;
import java.util.Objects;

public class ServerSelectFragment extends ListFragment {

    private List<Server> serverList;
    private RelativeLayout relativeLayout;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        serverList = PersistenceUtil.readAllServers(Objects.requireNonNull(this.getContext()));
        View v = inflater.inflate(R.layout.fragment_servers, container, false);
        ArrayAdapter<Server> adapter = new ArrayAdapter<>(inflater.getContext(), R.layout.fragment_servers_text,
                R.id.list_content, serverList);
        setListAdapter(adapter);
        relativeLayout = Objects.requireNonNull(getActivity()).findViewById(R.id.activity_main);
        relativeLayout.setVisibility(View.INVISIBLE);
        return v;
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        Server selectedServer = serverList.get(position);
        PersistenceUtil.writeSelectedServerConf(getContext(), selectedServer);
        Objects.requireNonNull(getActivity()).getIntent().putExtra(AppConstants.SERVER.toString(),
                selectedServer);
        Objects.requireNonNull(getActivity().getSupportFragmentManager().beginTransaction()
                .remove(this)).commit();
        ((MainActivity) getActivity()).updateViews();
        ((MainActivity) getActivity()).setFragmentTransaction(null);
        relativeLayout.setVisibility(View.VISIBLE);
    }

}