package vn.edu.ueh.thudinh.fashion2hand;

import java.util.*;

/** Only server-verified, available items contribute to checkout and its total. */
final class CartSelection {
    private final Set<String> chosen=new LinkedHashSet<>();
    private final Map<String,Long> prices=new LinkedHashMap<>();
    private boolean selectAll;
    void restore(Collection<String> ids){chosen.clear();if(ids!=null)chosen.addAll(ids);prices.clear();selectAll=false;}
    List<String> savedIds(){return new ArrayList<>(chosen);}
    void invalidate(){prices.clear();}
    void retain(Collection<String> ids){chosen.retainAll(ids);prices.keySet().retainAll(ids);if(ids.isEmpty())selectAll=false;}
    void pending(String id){prices.remove(id);}
    void update(String id,long price,boolean available){
        if(available&&price>0){prices.put(id,price);if(selectAll)chosen.add(id);}
        else{prices.remove(id);chosen.remove(id);}
    }
    void choose(String id,boolean checked){selectAll=false;if(checked&&prices.containsKey(id))chosen.add(id);else chosen.remove(id);}
    void chooseAll(boolean checked){selectAll=checked;chosen.clear();if(checked)chosen.addAll(prices.keySet());}
    boolean checked(String id){return chosen.contains(id);}
    boolean available(String id){return prices.containsKey(id);}
    int availableCount(){return prices.size();}
    List<String> selectedIds(){List<String> result=new ArrayList<>();for(String id:prices.keySet())if(chosen.contains(id))result.add(id);return result;}
    boolean allChecked(){return !prices.isEmpty()&&selectedIds().size()==prices.size();}
    long total(){long total=0;for(String id:selectedIds())total+=prices.get(id);return total;}
}
