/*
 * Copyright (c) 2019, halworsen <mwh@halvorsenfamilien.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.milestones.items;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.JPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.AsyncBufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.milestones.Milestone;
import net.runelite.client.plugins.milestones.MilestonesCategoryManager;
import net.runelite.client.plugins.milestones.MilestonesPlugin;

@Slf4j
@Singleton
public class ItemMilestoneManager extends MilestonesCategoryManager
{
	@Inject
	private MilestonesPlugin plugin;
	@Inject
	private ItemManager itemManager;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private Client client;

	private boolean loggingIn = false;
	private ItemMilestoneEditPanel editPanel;
	private Map<Integer, Integer> previousInventory = new HashMap<>();
	// Assigns milestones to item IDs
	// I.e. it maps milestone IDs to item IDs
	@Getter
	private Map<Integer, Integer> milestoneItemIdMap = new HashMap<>();

	ItemMilestoneManager()
	{
		this.categoryName = "Items";
		this.categoryMilestones = new ArrayList<>();
	}

	@Override
	public JPanel getEditPanel()
	{
		if (editPanel == null)
		{
			editPanel = new ItemMilestoneEditPanel(this, itemManager, clientThread, executor);
		}

		return editPanel;
	}

	@Override
	public boolean shouldWrapEditor()
	{
		return false;
	}

	@Override
	public void rebuildEditPanel()
	{
		editPanel.rebuildResults(true);
	}

	@Override
	public void resetEditPanel()
	{
		editPanel.reset();
	}

	@Override
	public AsyncBufferedImage getIcon(int milestoneId)
	{
		if (!containsMilestone(milestoneId))
		{
			return super.getIcon(milestoneId);
		}

		return itemManager.getImage(milestoneItemIdMap.get(milestoneId));
	}

	protected int getMIDByItemId(int itemId)
	{
		if (!hasMilestoneForItem(itemId))
		{
			return -1;
		}

		for (int milestoneId : categoryMilestones)
		{
			int milestoneItemId = milestoneItemIdMap.get(milestoneId);
			if (milestoneItemId == itemId)
			{
				return milestoneId;
			}
		}

		return -1;
	}

	// Returns the itemID connected to a milestone
	protected int getMilestoneItemId(int milestoneId)
	{
		if (!containsMilestone(milestoneId))
		{
			return -1;
		}

		return milestoneItemIdMap.get(milestoneId);
	}

	// Save data about what itemIDs are connected to each milestone
	public String getSaveData()
	{
		final Gson gson = new Gson();

		return gson.toJson(milestoneItemIdMap);
	}

	public void loadSaveData(String data)
	{
		final Gson gson = new Gson();

		categoryMilestones = new ArrayList<>();
		previousInventory = new HashMap<>();

		Map<Integer, Integer> storedMap = gson.fromJson(
				data,
				new TypeToken<Map<Integer, Integer>>()
				{ }.getType()
		);

		if (storedMap != null)
		{
			for (int milestoneId : storedMap.keySet())
			{
				categoryMilestones.add(milestoneId);
			}

			milestoneItemIdMap = storedMap;
		}
	}

	private void updatePreviousInv(Item[] items)
	{
		previousInventory = consolidateInventory(items);
	}

	// Stacks unstackables together in a hashmap
	private Map<Integer, Integer> consolidateInventory(Item[] items)
	{
		Map<Integer, Integer> consolidatedInventory = new HashMap<>();

		for (Item invItem : items)
		{
			int baseId = ItemVariationMapping.map(invItem.getId());

			if (consolidatedInventory.containsKey(baseId))
			{
				consolidatedInventory.put(baseId, consolidatedInventory.get(baseId) + invItem.getQuantity());
			}
			else
			{
				consolidatedInventory.put(baseId, invItem.getQuantity());
			}
		}

		return consolidatedInventory;
	}

	// Returns a map describing how the quantity of each itemID in the inventory has changed since the last call
	private Map<Integer, Integer> getInventoryDeltas(Item[] items)
	{
		Map<Integer, Integer> deltas = new HashMap<>();
		Map<Integer, Integer> consolidatedInventory = consolidateInventory(items);

		// Now we can go through the inventory and find how much each item quantity changed
		for (Map.Entry<Integer, Integer> item : consolidatedInventory.entrySet())
		{
			if (previousInventory.containsKey(item.getKey()))
			{
				int delta = item.getValue() - previousInventory.get(item.getKey());
				deltas.put(item.getKey(), delta);
			}
			else
			{
				deltas.put(item.getKey(), item.getValue());
			}
		}

		// Go through everything in the previous inventory that isn't in the current inventory anymore
		Map<Integer, Integer> prevInvCopy = new HashMap<>(previousInventory);
		prevInvCopy.keySet().removeAll(consolidatedInventory.keySet());
		for (Map.Entry<Integer, Integer> item : prevInvCopy.entrySet())
		{
			deltas.put(item.getKey(), -item.getValue());
		}

		previousInventory = consolidatedInventory;

		return deltas;
	}

	private boolean hasMilestoneForItem(int itemId)
	{
		return milestoneItemIdMap.containsValue(itemId);
	}

	private boolean isBanking()
	{
		Widget widget = client.getWidget(WidgetInfo.BANK_CONTAINER);

		if (widget == null)
		{
			return false;
		}

		return true;
	}

	private void updateMilestoneProgress(Item[] items)
	{
		Map<Integer, Integer> deltas = getInventoryDeltas(items);

		for (Map.Entry<Integer, Integer> pair : deltas.entrySet())
		{
			int itemId = pair.getKey();
			int delta = pair.getValue();

			/*
			 * Don't work with items we haven't set goals for
			 * Don't progress the goals when banking
			 */
			if (!hasMilestoneForItem(itemId) || isBanking())
			{
				continue;
			}

			progressMilestone(getMIDByItemId(itemId), delta);
		}
	}

	protected Milestone getMilestone(int milestoneId)
	{
		return plugin.getMilestoneById(milestoneId);
	}

	protected int addNewMilestone(String name, int progress, int amount, int itemId)
	{
		int milestoneId = addNewMilestone(name, progress, amount);

		milestoneItemIdMap.put(milestoneId, itemId);

		return milestoneId;
	}

	// Pass the call up the chain
	protected void updateMilestone(int milestoneId, int progress, int amount)
	{
		super.updateMilestone(milestoneId, progress, amount);
	}

	@Override
	protected void removeMilestone(int milestoneId)
	{
		super.removeMilestone(milestoneId);

		milestoneItemIdMap.remove(milestoneId);
	}

	// This is always called for the player inventory on login if the player inventory contains items.
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final ItemContainer container = event.getItemContainer();
		final ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);

		/*
		 * We need to know if there's something in the inventory when a player logs in since
		 * it's called as an item container change. Obviously, we don't want to track progress
		 * on items that are already in the inventory when you log in.
		 */
		if (loggingIn)
		{
			loggingIn = false;

			Item[] invItems;
			if (inventory == null)
			{
				invItems = new Item[]{};
			}
			else
			{
				invItems = inventory.getItems();
			}

			updatePreviousInv(invItems);
		}

		if (container == inventory)
		{
			updateMilestoneProgress(inventory.getItems());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();

		if (state == GameState.LOGGING_IN)
		{
			loggingIn = true;
		}
	}
}