package io.github.divios.lib.dLib;

import io.github.divios.core_lib.events.Events;
import io.github.divios.core_lib.events.Subscription;
import io.github.divios.core_lib.misc.timeStampUtils;
import io.github.divios.core_lib.scheduler.Schedulers;
import io.github.divios.core_lib.scheduler.Task;
import io.github.divios.core_lib.utils.Log;
import io.github.divios.dailyShop.DailyShop;
import io.github.divios.dailyShop.events.deletedShopEvent;
import io.github.divios.dailyShop.events.reStockShopEvent;
import io.github.divios.dailyShop.events.updateItemEvent;
import io.github.divios.dailyShop.guis.settings.shopGui;
import io.github.divios.lib.dLib.synchronizedGui.syncHashMenu;
import io.github.divios.lib.dLib.synchronizedGui.syncMenu;
import io.github.divios.lib.storage.dataManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.jetbrains.annotations.NotNull;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class dShop {

    private static final DailyShop plugin = DailyShop.getInstance();
    private static final dataManager dManager = dataManager.getInstance();

    private String name;
    private final dShopT type;
    private final Map<UUID, dItem> items = new LinkedHashMap<>();
    private final syncMenu guis;

    private Timestamp timestamp;
    private int timer;

    private final Set<Task> tasks = new HashSet<>();
    private final Set<Subscription> listeners = new HashSet<>();

    @Deprecated
    public dShop(String name, dShopT type) {
        this.name = name;
        this.type = type;
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.timer = plugin.configM.getSettingsYml().DEFAULT_TIMER;  // seconds representing time to pass until reset
        this.guis = syncHashMenu.create(this);

        ready();
    }

    @Deprecated
    public dShop(String name, dShopT type, String base64, Timestamp timestamp, int timer) {
        this.name = name;
        this.type = type;
        this.timestamp = timestamp;
        this.timer = timer;

        guis = syncHashMenu.fromJson(base64, this);
        ready();
    }

    @Deprecated
    public dShop(String name, dShopT type, String base64, Timestamp timestamp, int timer, Set<dItem> items) {
        this.name = name;
        this.type = type;
        this.timestamp = timestamp;
        this.timer = timer;
        items.forEach(dItem -> this.items.put(dItem.getUid(), dItem));

        guis = syncHashMenu.fromJson(base64, this);
        ready();
    }



    private void ready() {

        tasks.add(
                Schedulers.async().runRepeating(() -> {

                    if (timer == -1) return;
                    if (timeStampUtils.diff(timestamp, new Timestamp(System.currentTimeMillis())) >= timer)
                        reStock();

                }, 1, TimeUnit.SECONDS, 1, TimeUnit.SECONDS)
        );

        tasks.add(
                Schedulers.async().runRepeating(() -> {      // auto-update gui if any changes where made
                    dManager.asyncUpdateGui(this.name, guis);
                }, 15, TimeUnit.MINUTES, 15, TimeUnit.MINUTES)
        );

        Events.subscribe(deletedShopEvent.class, EventPriority.LOW)     // auto-destroy listener
                .biHandler((own, e) -> {
                    if (e.getShop().getName().equals(name)) {
                        destroy();
                        own.unregister();
                    }
                });

        listeners.add(
                Events.subscribe(reStockShopEvent.class)
                        .filter(o -> o.getShop().equals(this))
                        .handler(e -> reStock())
        );

    }

    private synchronized void reStock() {
        timestamp = new Timestamp(System.currentTimeMillis());
        dManager.updateTimeStamp(this.name, this.timestamp);
        Schedulers.sync().run(guis::reStock);
        dManager.asyncUpdateGui(this.name, this.guis);
    }

    /**
     * Opens the actual shop for the player
     *
     * @param p
     */
    public void openShop(Player p) {
        guis.generate(p);
    }

    /**
     * Opens the gui to manage the items of this shop
     *
     * @param p
     */
    public void manageItems(Player p) {
        shopGui.open(p, this);
    }

    /**
     * Opens the gui to customize the display of this shop
     *
     * @param p
     */
    public void openCustomizeGui(Player p) {
        guis.customizeGui(p);
    }

    /**
     * Gets the name of the shop
     *
     * @return
     */
    public synchronized String getName() {
        return name;
    }

    /**
     * Sets the name of the shop
     *
     * @param name
     */
    public synchronized void rename(String name) {
        this.name = name;
    }

    /**
     * Gets the type of the shop
     *
     * @return type of the shop (buy,sell)
     */
    public synchronized dShopT getType() {
        return type;
    }

    /**
     * Gets a copy the items in the shop
     *
     * @return returns a List of dItems. Note that this list is a copy of the original,
     * any change made to it won't affect the original one
     */
    public synchronized @NotNull
    Set<dItem> getItems() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(items.values()));
    }

    /**
     * Gets the item by uuid
     *
     * @param uid the UUID to search
     * @return null if it does not exist
     */
    public synchronized Optional<dItem> getItem(UUID uid) {
        return Optional.ofNullable(items.get(uid));
    }

    /**
     * Checks if the shop has a particular item
     *
     * @param uid the UUID to check
     * @return true if exits, false if not
     */
    public synchronized boolean hasItem(UUID uid) {
        return getItem(uid).isPresent();
    }

    /**
     * Updates the item of the shop
     *
     * @param uid
     * @param newItem
     */
    public synchronized void updateItem(UUID uid, dItem newItem) {
        if (!items.containsKey(uid))
            return; // Throw error

        items.put(uid, newItem);
        Events.callEvent(new updateItemEvent(newItem, updateItemEvent.updatetype.UPDATE_ITEM, this));    // Event to update item
        dManager.updateItem(getName(), newItem);


    }


    /**
     * Sets the items of this shop
     */
    public synchronized void setItems(@NotNull Set<dItem> items) {

        Map<UUID, dItem> newItems = new HashMap<>();
        items.forEach(dItem -> newItems.put(dItem.getUid(), dItem));            // Cache values for a O(1) search

        for (Iterator<Map.Entry<UUID, dItem>> it = this.items.entrySet().iterator(); it.hasNext(); ) {          // Remove items that are not on the newItems list
            Map.Entry<UUID, dItem> entry = it.next();
            if (newItems.containsKey(entry.getKey())) continue;

            Events.callEvent(new updateItemEvent(entry.getValue(), updateItemEvent.updatetype.DELETE_ITEM, this));
            dManager.deleteItem(name, entry.getKey());
            it.remove();
        }

        items.forEach(this::addItem);       // Replace the old values for the new ones
    }

    /**
     * Adds an item to this shop
     *
     * @param item item to be added
     */
    public synchronized void addItem(@NotNull dItem item) {
        items.put(item.getUid(), item);
        dManager.addItem(this.name, item);
    }

    /**
     * Removes an item from the shop
     *
     * @param uid UUID of the item to be removed
     * @return true if the item was removed. False if not
     */
    public synchronized boolean removeItem(UUID uid) {

        dItem removed = items.remove(uid);

        if (removed == null) return false;
        dManager.deleteItem(this.name, uid);
        Events.callEvent(new updateItemEvent(removed, updateItemEvent.updatetype.DELETE_ITEM, this));
        return true;
    }


    /**
     * Return the dGui of this shop
     *
     * @return
     */
    public synchronized syncMenu getGuis() {
        return guis;
    }

    protected synchronized void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public synchronized Timestamp getTimestamp() {
        return this.timestamp;
    }

    public synchronized int getTimer() {
        return timer;
    }

    public synchronized void setTimer(int timer) {
        this.timer = timer;
        dManager.updateTimer(this.name, this.timer);
    }

    public synchronized void destroy() {
        guis.destroy();
        tasks.forEach(Task::stop);
        tasks.clear();
        listeners.forEach(Subscription::unregister);
        listeners.clear();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof dShop &&
                this.getName().equals(((dShop) o).getName());
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }


    public enum dShopT {
        buy,
        sell
    }

}
