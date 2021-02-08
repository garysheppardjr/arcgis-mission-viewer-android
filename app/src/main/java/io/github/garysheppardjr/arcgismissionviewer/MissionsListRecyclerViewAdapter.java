package io.github.garysheppardjr.arcgismissionviewer;

import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

class MissionsListRecyclerViewAdapter extends RecyclerView.Adapter<MissionsListRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = MissionsListRecyclerViewAdapter.class.getSimpleName();

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.textView_itemId);
            itemView.setOnClickListener(v -> {
                TextView textView_itemId = v.findViewById(R.id.textView_itemId);
                Log.d(TAG, "That's a click on mission " + textView_itemId.getText());
                v.getContext().startActivity(
                        new Intent(v.getContext(), MissionActivity.class)
                                .putExtra(MissionActivity.EXTRA_PORTAL_URL, portalUrl)
                                .putExtra(MissionActivity.EXTRA_MISSION_ID, textView.getText())
                );
            });
        }

        public TextView getTextView() {
            return textView;
        }

    }

    private final String portalUrl;
    private final List<String> missionIds;

    public MissionsListRecyclerViewAdapter(String portalUrl, List<String> missionIds) {
        this.portalUrl = portalUrl;
        this.missionIds = missionIds;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View v = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.mission_list_item, viewGroup, false);

        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        Log.d(TAG, "Element " + position + " set.");
        viewHolder.getTextView().setText(missionIds.get(position));
    }

    @Override
    public int getItemCount() {
        return missionIds.size();
    }

}
