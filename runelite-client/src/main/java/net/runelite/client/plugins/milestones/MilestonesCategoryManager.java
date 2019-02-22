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

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.AsyncBufferedImage;

/**
 * Milestone category managers are supposed to serve as the main logic handlers and
 * as the interface to the plugin for each individual category
 */
@Singleton
@Slf4j
public abstract class MilestonesCategoryManager
{
	@Inject
	protected MilestonesPlugin plugin;
	// Should be human readable. This is displayed on the collapsible panel header in the edit tab
	@Getter(AccessLevel.PUBLIC)
	protected String categoryName = "invalidcategory";
	// Holds all the milestones tied to this category
	protected Map<Integer, Milestone> categoryMilestones = new HashMap<>();
	private ImageIcon defaultIcon = new ImageIcon(MilestonesPlugin.class.getResource("unknown_category.png"));

	// This should return the panel to be shown in the edit tab for the category
	public JPanel getEditPanel()
	{
		return new JPanel();
	}

	/*
	  * Whether or not to wrap the editor panel in a scrollpane in the edit panel.
	  * Override this if you want to handle scrolling internally in the edit panel
	 */
	public boolean shouldWrapEditor()
	{
		return true;
	}

	// Should return a icon to display on the milestone card for the given milestone ID
	public AsyncBufferedImage getIcon(int milestoneId)
	{
		AsyncBufferedImage bufferedImage = new AsyncBufferedImage(
				defaultIcon.getIconWidth(),
				defaultIcon.getIconHeight(),
				BufferedImage.TYPE_INT_RGB
		);

		log.debug("Abstract superclass is providing the default icon for milestone #" + milestoneId);
		return bufferedImage;
	}

	public boolean containsMilestone(int milestoneId)
	{
		return categoryMilestones.containsKey(milestoneId);
	}

	public boolean containsMilestone(Milestone milestone)
	{
		return categoryMilestones.containsValue(milestone);
	}

	// All milestone creation/updates/removals should go through the manager
	protected int addNewMilestone(Milestone newMilestone)
	{
		int milestoneId = plugin.addNewMilestone(newMilestone);

		categoryMilestones.put(milestoneId, newMilestone);

		return milestoneId;
	}

	protected void progressMilestone(int milestoneId, int amount)
	{
		if (!categoryMilestones.containsKey(milestoneId))
		{
			throw new IllegalArgumentException("Attempt to progress milestone #" + milestoneId + " in the " + getCategoryName() + " category, but this milestone was never tracked by the category manager!");
		}

		plugin.progressMilestone(milestoneId, amount);
	}

	// This doesn't do anything but pass the call up the chain, but is necessary because the plugin needs to update the milestone panel
	protected void updateMilestone(int milestoneId, int progress, int amount)
	{
		plugin.updateMilestone(milestoneId, progress, amount);
	}

	protected void removeMilestone(int milestoneId)
	{
		if (!categoryMilestones.containsKey(milestoneId))
		{
			throw new IllegalArgumentException("Attempt to remove milestone #" + milestoneId + " in the " + getCategoryName() + " category, but this milestone was never tracked by the category manager!");
		}

		plugin.removeMilestone(milestoneId);

		categoryMilestones.remove(milestoneId);
	}

	// This should only return milestones relevant to the category, i.e. only item milestones for the item category
	public Collection<Milestone> getCategoryMilestones()
	{
		return categoryMilestones.values();
	}

	// This is called on rebuildMilestones() in the plugin panel and should rebuild the edit panel if it depends on milestone data
	abstract public void rebuildEditPanel();
	// This is called when a player logs out. The edit panel should be reset to its initial state in this.
	abstract public void resetEditPanel();

	// This should return a GSON string with any data the category manager wants to save for the user
	abstract public String getSaveData();
	// This should parse the GSON string and load the data in it
	abstract public void loadSaveData(String data);
}
