package vn.edu.ueh.thudinh.fashion2hand;

import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import java.util.*;

/** Always search the complete directory, never the previous filtered result. */
final class AddressSearchAdapter extends ArrayAdapter<String> {
    private final List<String> all;
    AddressSearchAdapter(Context context,Collection<String> entries){
        super(context,android.R.layout.simple_dropdown_item_1line,new ArrayList<>(entries));all=new ArrayList<>(entries);
    }
    private final Filter search=new Filter(){
        @Override protected FilterResults performFiltering(CharSequence constraint){
            List<String> found=new ArrayList<>();String query=constraint==null?"":constraint.toString();
            for(String name:all)if(AddressSearch.matches(name,query))found.add(name);
            FilterResults results=new FilterResults();results.values=found;results.count=found.size();return results;
        }
        @SuppressWarnings("unchecked") @Override protected void publishResults(CharSequence constraint,FilterResults results){
            setNotifyOnChange(false);clear();addAll((List<String>)results.values);notifyDataSetChanged();
        }
        @Override public CharSequence convertResultToString(Object value){return String.valueOf(value);}
    };
    @Override public Filter getFilter(){return search;}
    boolean isExactEntry(String value){return all.contains(value);}
}
