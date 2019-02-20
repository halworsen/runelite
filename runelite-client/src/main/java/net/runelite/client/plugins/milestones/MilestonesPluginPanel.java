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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.CollapsibleCategory;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/*
 * The structure of this can be a bit confusing, here's an explanation:
 * A main container (plain JPanel) holds a panel container (tied to a CardLayout).
 * That panel container shows two cards:
 * 	1. A not logged in error panel. This is shown while you're logged out
 * 	2. The actual content of the plugin panel (contentContainer)
 * The contentContainer contains the tab group at the top of the panel,
 * and the actual content of the plugin panel (held in currentViewContainer)
 * currentViewContainer contains both the milestones listing panel and the add milestone panel. It is controlled by the tab group.
 *
 * In the milestones list view, milestonesCardLayout controls whether an error panel is shown (when there are no milestones) and
 * when the actual list is shown.
 */
@Singleton
@Slf4j
public class MilestonesPluginPanel extends PluginPanel
{
	private final String MILESTONES_PANEL = "MILESTONES_PANEL";
	private final String NO_MILESTONES_PANEL = "NO_MILESTONES_PANEL";

	private final String CONTENT_PANEL = "CONTENT_PANEL";
	private final String NOT_LOGGED_IN_PANEL = "NOT_LOGGED_IN_PANEL";

	private final CardLayout panelCardLayout = new CardLayout();
	private final CardLayout milestonesCardLayout = new CardLayout();

	// Holds everything in the panel
	private final JPanel mainContainer = new JPanel();
	// Holds the content container & not logged in panel
	private final JPanel panelContainer = new JPanel(panelCardLayout);
	private final PluginErrorPanel notLoggedInPanel = new PluginErrorPanel();

	// Holds the tabgroup & current view (milestones or edit tab)
	private final JPanel contentContainer = new JPanel();
	// Holds the milestone list or the add milestone panel
	private final JPanel currentViewContainer = new JPanel();

	private final PluginErrorPanel noMilestonesPanel = new PluginErrorPanel();
	// Contains the actual milestones panel and the no milestones error panel
	private final JPanel milestonesContainer = new JPanel(milestonesCardLayout);
	// Holds all the milestone cards
	private final JPanel milestoneListPanel = new JPanel();

	private final MaterialTabGroup tabGroup = new MaterialTabGroup(currentViewContainer);

	private final JPanel editMilestonePanel = new JPanel();

	@Inject
	private ClientThread clientThread;

	@Inject
	private MilestonesPlugin plugin;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	MilestonesPluginPanel(MilestonesPlugin plugin, ClientThread clientThread, ItemManager itemManager, ScheduledExecutorService executor)
	{
		// Disable wrapping since we make our own scrollpanes for the milestones/item search results
		super(false);

		this.plugin = plugin;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.executor = executor;

		init();
	}

	private void init()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		mainContainer.setLayout(new BorderLayout());

		contentContainer.setLayout(new BorderLayout());

		currentViewContainer.setLayout(new BorderLayout());
		currentViewContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

		milestoneListPanel.setLayout(new DynamicGridLayout(0, 1, 0, 5));
		milestoneListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Wrapper for the not logged in error panel
		JPanel notLoggedInWrapper = new JPanel();
		notLoggedInWrapper.setLayout(new BorderLayout());
		notLoggedInPanel.setContent("Milestones", "Log in to view and edit your milestones.");
		notLoggedInWrapper.add(notLoggedInPanel, BorderLayout.NORTH);

		// Wrapper for the milestones list
		JPanel milestonesWrapper = new JPanel();
		milestonesWrapper.setLayout(new BorderLayout());
		milestonesWrapper.add(milestoneListPanel, BorderLayout.NORTH);

		// Give the milestones list a scrollbar
		JScrollPane milestonesScrollWrapper = new JScrollPane(milestonesWrapper);
		milestonesScrollWrapper.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
		milestonesScrollWrapper.getVerticalScrollBar().setBorder(new EmptyBorder(0, 5, 0, 0));
		milestonesScrollWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		milestonesScrollWrapper.setVisible(false);

		// Wrapper for the no milestones error panel
		JPanel noMilestonesWrapper = new JPanel();
		noMilestonesWrapper.setLayout(new BorderLayout());
		noMilestonesPanel.setContent("Milestones", "You have not set any milestones.");
		noMilestonesWrapper.add(noMilestonesPanel, BorderLayout.NORTH);

		// Edit milestone panels
		JPanel editMilestoneGrid = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
		editMilestoneGrid.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Add the milestone editor panel associated with each category
		for (MilestonesCategoryManager manager : plugin.getCategoryManagers())
		{
			JPanel categoryEditPanel = manager.getEditPanel();
			// Wrap the edit panel in a collapsible panel
			CollapsibleCategory categoryHolder = new CollapsibleCategory(manager.getCategoryName(), categoryEditPanel);

			editMilestoneGrid.add(categoryHolder);
		}

		editMilestonePanel.setLayout(new BorderLayout());
		editMilestonePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		editMilestonePanel.add(editMilestoneGrid, BorderLayout.CENTER);

		milestonesContainer.add(milestonesScrollWrapper, MILESTONES_PANEL);
		milestonesContainer.add(noMilestonesWrapper, NO_MILESTONES_PANEL);
		milestonesCardLayout.show(milestonesContainer, plugin.hasNoMilestones() ? NO_MILESTONES_PANEL : MILESTONES_PANEL);

		rebuildMilestones();

		MaterialTab milestonesTab = new MaterialTab("Milestones", tabGroup, milestonesContainer);
		MaterialTab editTab = new MaterialTab("Edit", tabGroup, editMilestonePanel);

		tabGroup.setBorder(new EmptyBorder(5, 0, 0, 0));
		tabGroup.addTab(milestonesTab);
		tabGroup.addTab(editTab);
		tabGroup.select(milestonesTab);

		contentContainer.add(tabGroup, BorderLayout.NORTH);
		contentContainer.add(currentViewContainer, BorderLayout.CENTER);

		panelContainer.add(contentContainer, CONTENT_PANEL);
		panelContainer.add(notLoggedInWrapper, NOT_LOGGED_IN_PANEL);
		panelCardLayout.show(panelContainer, NOT_LOGGED_IN_PANEL);

		mainContainer.add(panelContainer, BorderLayout.CENTER);
		add(mainContainer, BorderLayout.CENTER);
	}

	protected void setLoggedIn(boolean isLoggedIn)
	{
		panelCardLayout.show(panelContainer, isLoggedIn ? CONTENT_PANEL : NOT_LOGGED_IN_PANEL);
	}

	protected void rebuildMilestones()
	{
		milestoneListPanel.removeAll();

		for (Milestone milestone : plugin.getUserMilestones())
		{
			MilestonesCategoryManager manager = plugin.getCategoryManager(milestone.getCategory());
			MilestonesMilestoneCard milestoneCard = new MilestonesMilestoneCard(manager, milestone, itemManager);
			milestoneListPanel.add(milestoneCard);
		}

		// Rebuild the category edit panels since they may use milestone data in their panels
		for (MilestonesCategoryManager manager : plugin.getCategoryManagers())
		{
			manager.rebuildEditPanel();
		}

		milestoneListPanel.revalidate();
		milestoneListPanel.repaint();

		milestonesCardLayout.show(milestonesContainer, plugin.hasNoMilestones() ? NO_MILESTONES_PANEL : MILESTONES_PANEL);
	}

	protected void fullReset()
	{
		milestoneListPanel.removeAll();

		// Reset the edit panels
		for (MilestonesCategoryManager manager : plugin.getCategoryManagers())
		{
			manager.resetEditPanel();
		}

		milestoneListPanel.revalidate();
		milestoneListPanel.repaint();

		milestonesCardLayout.show(milestonesContainer, NO_MILESTONES_PANEL);
	}
}
