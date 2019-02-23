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
package net.runelite.client.plugins.milestones;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.milestones.items.ItemMilestoneManager;
import net.runelite.client.plugins.milestones.skills.SkillMilestoneManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor
(
	name = "Milestones",
	description = "Set milestones for your account and track your progress towards them",
	tags = {"drops", "achievements", "quest", "diary", "skill"},
	enabledByDefault = false
)
@Singleton
@Slf4j
public class MilestonesPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private Client client;
	@Inject
	private MilestonesConfig config;
	@Inject
	private EventBus eventBus;

	@Getter(AccessLevel.PACKAGE)
	private NavigationButton navButton;
	@Getter(AccessLevel.PACKAGE)
	private MilestonesPluginPanel pluginPanel;
	@Getter
	private ArrayList<Milestone> userMilestones = new ArrayList<>();
	@Getter
	private String loggedInAs = "";
	private Map<String, MilestonesCategoryManager> milestoneCategories = new HashMap<>();
	// Holds the next milestoneId to use
	private int nextMilestoneId = 1;

	// Milestone category managers
	@Inject
	private ItemMilestoneManager itemMilestoneManager;
	@Inject
	private SkillMilestoneManager skillMilestoneManager;
	//@Inject
	//private QuestMilestoneManager questMilestoneManager;

	@Provides
	MilestonesConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MilestonesConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Add each milestone category manager to the map of managers
		milestoneCategories.put(itemMilestoneManager.getCategoryName(), itemMilestoneManager);
		milestoneCategories.put(skillMilestoneManager.getCategoryName(), skillMilestoneManager);
		//milestoneCategories.put(questMilestoneManager.getCategoryName(), questMilestoneManager);

		//config.milestoneData("");
		// Register each manager to the event bus
		for (MilestonesCategoryManager manager : milestoneCategories.values())
		{
			eventBus.register(manager);
		}

		pluginPanel = injector.getInstance(MilestonesPluginPanel.class);

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Milestones")
				.icon(icon)
				.priority(6)
				.panel(pluginPanel)
				.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		if (isLoggedIn())
		{
			storeUserMilestones(loggedInAs);
		}

		clientToolbar.removeNavigation(navButton);
	}

	public Collection<MilestonesCategoryManager> getCategoryManagers()
	{
		return milestoneCategories.values();
	}

	public MilestonesCategoryManager getCategoryManager(String name)
	{
		for (MilestonesCategoryManager manager : getCategoryManagers())
		{
			if (manager.getCategoryName().equalsIgnoreCase(name))
			{
				return manager;
			}
		}

		return null;
	}

	public Milestone getMilestoneById(int milestoneId)
	{
		for (Milestone milestone : userMilestones)
		{
			if (milestone.getId() == milestoneId)
			{
				return milestone;
			}
		}

		return null;
	}

	public int addNewMilestone(String name, int progress, int amount)
	{
		Milestone milestone = new Milestone();
		milestone.setName(name);
		milestone.setProgress(progress);
		milestone.setTarget(amount);
		milestone.setId(nextMilestoneId);

		userMilestones.add(milestone);

		SwingUtilities.invokeLater(() -> pluginPanel.rebuildMilestones());

		return nextMilestoneId++;
	}

	/*
	 * Note that progress is only capped upwards. The item manager uses negative progress to make sure
	 * that it tracks progress from the point the milestone was created.
	*/
	public void progressMilestone(int milestoneId, int amount)
	{
		Milestone milestone = getMilestoneById(milestoneId);

		int newProgress = Math.min(milestone.getProgress() + amount, milestone.getTarget());
		milestone.setProgress(newProgress);

		SwingUtilities.invokeLater(() -> pluginPanel.rebuildMilestones());
	}

	public void updateMilestone(int milestoneId, String name, int progress, int amount)
	{
		Milestone milestone = getMilestoneById(milestoneId);

		milestone.setName(name);
		milestone.setTarget(amount);
		milestone.setProgress(progress);

		SwingUtilities.invokeLater(() -> pluginPanel.rebuildMilestones());
	}

	public void updateMilestone(int milestoneId, int progress, int amount)
	{
		Milestone milestone = getMilestoneById(milestoneId);

		milestone.setTarget(amount);
		milestone.setProgress(progress);

		SwingUtilities.invokeLater(() -> pluginPanel.rebuildMilestones());
	}

	public void updateMilestoneProgress(int milestoneId, int progress)
	{
		Milestone milestone = getMilestoneById(milestoneId);

		milestone.setProgress(progress);

		SwingUtilities.invokeLater(() -> pluginPanel.rebuildMilestones());
	}

	public void removeMilestone(int milestoneId)
	{
		for (Milestone milestone : userMilestones)
		{
			if (milestone.getId() == milestoneId)
			{
				userMilestones.remove(milestone);

				break;
			}
		}

		SwingUtilities.invokeLater(() -> pluginPanel.rebuildMilestones());
	}

	public boolean hasNoMilestones()
	{
		return getUserMilestones().isEmpty();
	}

	/*
	 * Plugin data is stored like so:
	 *	  username1:
	 *	  	milestones: more gson
	 *	  	categories: more gson
	 *	  username2:
	 *	  	milestones: more gson
	 *	  	categories: more gson
	 *	  ...
	 *
	 * Where milestones contain the actual milestones GSON for the user, and is parsed in this class.
	 * The GSON in categories is then parsed to get the category names and data out, at which point you end up with
	 * something like:
	 *
	 *	  username:
	 *	  	milestones: more gson (already parsed. we're done with this)
	 *	  	categories:
	 *	  		items: even more GSON
	 *	  		skills: even more GSON
	 *	  		quests: even more GSON
	 *
	 * the "even more GSON" is passed to the relevant category's manager to be parsed. It's done this way because
	 * each category might need to store different data about the milestones. E.g. the item manager will want to
	 * associate itemIDs with its milestones, the skill manager will want to associate skill IDs with its milestones, etc.
	 *
	 * Is it overly complicated? Probably. Couldn't you just store the category GSON directly alongside milestone GSON?
	 * Yup, but then you couldn't make milestone milestones without giving the manager a bad name (i.e. not "milestones"),
	 * and I like having all options available without making special exceptions. It feels more robust.
	 */
	private void storeUserMilestones(String username)
	{
		final Gson gson = new Gson();

		// Fetch the entire config to avoid overwriting other users' milestones
		String dataJSON = config.milestoneData();
		Map<String, Map<String, String>> milestoneData = gson.fromJson(
				dataJSON,
				new TypeToken<Map<String, Map<String, String>>>()
				{ }.getType()
		);

		if (milestoneData == null || milestoneData.isEmpty())
		{
			milestoneData = new HashMap<>();
		}


		Map<String, String> dataTypeMap = new HashMap<>();

		String milestonesGSON = gson.toJson(userMilestones);
		dataTypeMap.put("milestones", milestonesGSON);

		// categoryDataGSON = [category1: saveDataGSON, category2: saveDataGSON, ...]
		Map<String, String> categoryDataMap = new HashMap<>();
		for (Map.Entry<String, MilestonesCategoryManager> entry : milestoneCategories.entrySet())
		{
			categoryDataMap.put(entry.getKey(), entry.getValue().getSaveData());
		}

		// [milestones: milestonesGSON, categories: categoryDataGSON]
		String categoryDataGSON = gson.toJson(categoryDataMap);
		dataTypeMap.put("categories", categoryDataGSON);

		milestoneData.put(username, dataTypeMap);

		String userDataGSON = gson.toJson(milestoneData);
		config.milestoneData(userDataGSON);
	}

	private void fetchUserMilestones(String username)
	{
		final Gson gson = new Gson();

		String dataJSON = config.milestoneData();
		Map<String, Map<String, String>> milestoneData = gson.fromJson(
				dataJSON,
				new TypeToken<Map<String, Map<String, String>>>()
				{ }.getType()
		);

		// There was no data at all
		if (milestoneData == null)
		{
			return;
		}

		if (milestoneData.containsKey(username))
		{
			Map<String, String> dataTypeMap = milestoneData.get(username);

			// First we load the user milestones
			userMilestones = gson.fromJson(
					dataTypeMap.get("milestones"),
					new TypeToken<ArrayList<Milestone>>()
					{ }.getType()
			);

			// Then we get all the category data
			Map<String, String> categoryDataMap = gson.fromJson(
					dataTypeMap.get("categories"),
					new TypeToken<Map<String, String>>()
					{ }.getType()
			);

			// Ask all the managers to load their data
			for (Map.Entry<String, String> entry : categoryDataMap.entrySet())
			{
				milestoneCategories.get(entry.getKey()).loadSaveData(entry.getValue());
			}
		}

		// Finally, we find the largest milestone ID so we can continue counting it upwards
		for (Milestone milestone : userMilestones)
		{
			if (milestone.getId() > nextMilestoneId)
			{
				nextMilestoneId = milestone.getId();
			}
		}
	}

	public boolean isLoggedIn()
	{
		return !loggedInAs.isEmpty();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState state = gameStateChanged.getGameState();

		if (state == GameState.LOGGED_IN)
		{
			// We've already loaded the user milestones. Prevents loading old milestones when e.g. a new chunk loads
			if (isLoggedIn())
			{
				return;
			}

			// Store the username because there's no way to get it when the gamestate changes to LOGIN_SCREEN
			loggedInAs = client.getUsername();

			// Retrieve this user's milestones from config
			fetchUserMilestones(loggedInAs);

			SwingUtilities.invokeLater(() ->
			{
				pluginPanel.rebuildMilestones();
				pluginPanel.setLoggedIn(true);
			});
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			/*
			 * loggedInAs will only contain a username if the player logged in first,
			 * so this is to check if the gamestate change was a logout.
			*/
			if (!isLoggedIn())
			{
				return;
			}

			// Store this user's milestones in config
			log.debug("Storing user milestones for " + loggedInAs);
			storeUserMilestones(loggedInAs);
			// Clear the milestones
			userMilestones = new ArrayList<>();
			// Clear the milestones panel
			SwingUtilities.invokeLater(() ->
			{
				pluginPanel.fullReset();
				pluginPanel.setLoggedIn(false);
			});

			// If you fail login, onGameStateChanged is called again. So we reset loggedInAs to prevent overwriting saves
			loggedInAs = "";
		}
	}
}
