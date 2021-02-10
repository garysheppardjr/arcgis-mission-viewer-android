package io.github.garysheppardjr.arcgismissionviewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MissionsListFragment extends Fragment {

    private static final String TAG = MissionsListFragment.class.getSimpleName();

    private final String portalUrl;
    private final List<String> missionIds;

    private RecyclerView recyclerView;

    public MissionsListFragment() {
        this(null, null);
    }

    private MissionsListFragment(String portalUrl, List<String> missionIds) {
        this.portalUrl = portalUrl;
        this.missionIds = missionIds;
    }

    public static MissionsListFragment newInstance(String portalUrl, List<String> missionIds) {
        return new MissionsListFragment(portalUrl, missionIds);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_the_recycler_view, container, false);
        rootView.setTag(TAG);

        recyclerView = rootView.findViewById(R.id.recyclerView);
        recyclerView.setAdapter(new MissionsListRecyclerViewAdapter(portalUrl, missionIds));

        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        return rootView;
    }

}
