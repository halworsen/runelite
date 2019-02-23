package net.runelite.client.plugins.milestones.skills;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.events.ExperienceChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.AsyncBufferedImage;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.milestones.MilestonesCategoryManager;
import net.runelite.client.plugins.milestones.MilestonesPlugin;

@Singleton
@Slf4j
public class SkillMilestoneManager extends MilestonesCategoryManager
{

	@Inject
	private Client client;
	@Inject
	private MilestonesPlugin plugin;
	@Inject
	private SkillIconManager iconManager;

	private SkillMilestoneEditPanel editPanel;

	// Maps milestone IDs to skill info
	private Map<Integer, SkillMilestoneInfo> midToSkillInfo = new HashMap<>();

	SkillMilestoneManager()
	{
		this.categoryName = "Skills";
		this.categoryMilestones = new ArrayList<>();
	}

	@Override
	public JPanel getEditPanel()
	{
		if (editPanel == null)
		{
			editPanel = new SkillMilestoneEditPanel(this, iconManager, client);
		}

		return editPanel;
	}

	@Override
	public boolean shouldWrapEditor()
	{
		return false;
	}

	public void rebuildEditPanel()
	{

	}

	public void resetEditPanel()
	{

	}

	@Override
	public AsyncBufferedImage getIcon(int milestoneId)
	{
		Skill skill = midToSkillInfo.get(milestoneId).getSkill();
		ImageIcon skillIcon = new ImageIcon(iconManager.getSkillImage(skill, true));

		// Convert the skill icon to an AsyncBufferedImage
		AsyncBufferedImage bufferedImage = new AsyncBufferedImage(
				skillIcon.getIconWidth(),
				skillIcon.getIconHeight(),
				BufferedImage.TYPE_INT_ARGB
		);
		Graphics g = bufferedImage.createGraphics();
		skillIcon.paintIcon(null, g, 0, 0);
		g.dispose();

		return bufferedImage;
	}

	protected int getTrueTarget(int milestoneId)
	{
		return midToSkillInfo.get(milestoneId).getTrueTarget();
	}

	protected int getTotalXP()
	{
		int prevTotal = 0;
		int total = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}

			total += client.getSkillExperience(skill);
			// TODO: Max XP is greater than the max size of an integer, which can cause overflows. This category needs a larger rewrite to use longs
			if (total < prevTotal)
			{
				return Integer.MAX_VALUE;
			}

			prevTotal = total;
		}

		return total;
	}

	// The client can report wrong total levels (i.e. right after a XP change that resulted in a level up). Funnily enough it doesn't report wrong XP
	private int getTotalLevel()
	{
		int total = 0;
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}

			total += Experience.getLevelForXp(client.getSkillExperience(skill));
		}

		return total;
	}

	protected int getMilestoneBySkill(Skill skill)
	{
		if (!hasMilestoneForSkill(skill))
		{
			return -1;
		}

		for (Map.Entry<Integer, SkillMilestoneInfo> entry : midToSkillInfo.entrySet())
		{
			if (entry.getValue().getSkill() == skill)
			{
				return entry.getKey();
			}
		}

		return -1;
	}

	protected boolean hasMilestoneForSkill(Skill skill)
	{
		for (Map.Entry<Integer, SkillMilestoneInfo> entry : midToSkillInfo.entrySet())
		{
			if (entry.getValue().getSkill() == skill)
			{
				return true;
			}
		}

		return false;
	}

	private String getMilestoneName(Skill skill, boolean isLevelMilestone)
	{
		String skillName = skill == Skill.OVERALL ? "Total" : skill.getName();
		return skillName + " " + (isLevelMilestone ? "level" : "XP");
	}

	// TODO: (bug) something goes horribly wrong when you add new milestones on top of old ones?
	protected int addNewMilestone(Skill skill, int amount, boolean isLevelMilestone)
	{
		SkillMilestoneInfo info = new SkillMilestoneInfo();
		info.setSkill(skill);
		info.setTrueTarget(amount);
		info.setLevelMilestone(isLevelMilestone);

		String milestoneName = getMilestoneName(skill, isLevelMilestone);

		// Total level/XP is handled separately
		if (skill == Skill.OVERALL)
		{
			int progress = isLevelMilestone ? client.getTotalLevel() : getTotalXP();
			info.setTrueProgress(progress);

			int milestoneId = addNewMilestone(milestoneName, progress, amount);
			midToSkillInfo.put(milestoneId, info);

			return milestoneId;
		}

		int trueProgress = client.getSkillExperience(skill);
		int milestoneProgress = trueProgress;
		int milestoneAmount = amount;

		if (isLevelMilestone)
		{
			milestoneProgress = Experience.getLevelForXp(trueProgress);
			milestoneAmount = Experience.getLevelForXp(amount);
		}

		int milestoneId = addNewMilestone(milestoneName, milestoneProgress, milestoneAmount);
		// Store experience progress
		info.setTrueProgress(trueProgress);
		midToSkillInfo.put(milestoneId, info);

		return milestoneId;
	}

	protected void updateMilestone(int milestoneId, int amount, boolean isLevelMilestone)
	{
		if (!containsMilestone(milestoneId))
		{
			throw new IllegalStateException("Attempt to update milestone not belonging by the skills category");
		}

		Skill skill = midToSkillInfo.get(milestoneId).getSkill();
		SkillMilestoneInfo info = midToSkillInfo.get(milestoneId);
		info.setTrueTarget(amount);
		info.setLevelMilestone(isLevelMilestone);

		String milestoneName = getMilestoneName(skill, isLevelMilestone);

		if (skill == Skill.OVERALL)
		{
			int progress = isLevelMilestone ? client.getTotalLevel() : getTotalXP();
			info.setTrueProgress(progress);
			updateMilestone(milestoneId, milestoneName, progress, amount);

			return;
		}

		int trueProgress = client.getSkillExperience(skill);
		int milestoneProgress = trueProgress;
		int milestoneAmount = amount;
		if (isLevelMilestone)
		{
			milestoneProgress = Experience.getLevelForXp(trueProgress);
			milestoneAmount = Experience.getLevelForXp(amount);
		}

		info.setTrueProgress(trueProgress);
		updateMilestone(milestoneId, milestoneName, milestoneProgress, milestoneAmount);
	}

	private void updateMilestoneProgress(int milestoneId, int progress)
	{
		plugin.updateMilestoneProgress(milestoneId, progress);
	}

	@Override
	protected void removeMilestone(int milestoneId)
	{
		super.removeMilestone(milestoneId);

		midToSkillInfo.remove(milestoneId);
	}

	public String getSaveData()
	{
		final Gson gson = new Gson();

		return gson.toJson(midToSkillInfo);
	}

	public void loadSaveData(String data)
	{
		midToSkillInfo = new HashMap<>();
		categoryMilestones = new ArrayList<>();

		final Gson gson = new Gson();

		Map<Integer, SkillMilestoneInfo> storedInfo = gson.fromJson(
				data,
				new TypeToken<Map<Integer, SkillMilestoneInfo>>()
				{ }.getType()
		);

		if (storedInfo != null)
		{
			midToSkillInfo = storedInfo;

			for (int milestoneId : storedInfo.keySet())
			{
				categoryMilestones.add(milestoneId);
			}
		}
	}

	@Subscribe
	public void onExperienceChanged(ExperienceChanged event)
	{
		final Skill skill = event.getSkill();

		int milestoneId = getMilestoneBySkill(skill);
		if (milestoneId == -1)
		{
			return;
		}

		SkillMilestoneInfo info = midToSkillInfo.get(milestoneId);

		int trueProgress = client.getSkillExperience(skill);
		int milestoneProgress = info.isLevelMilestone() ? Experience.getLevelForXp(trueProgress) : trueProgress;

		info.setTrueProgress(trueProgress);
		updateMilestoneProgress(milestoneId, milestoneProgress);

		// Update total skill milestone
		milestoneId = getMilestoneBySkill(Skill.OVERALL);
		if (milestoneId == -1)
		{
			return;
		}

		info = midToSkillInfo.get(milestoneId);

		// TODO: Get the real total level at this point somehow. It seems to lag behind by 1 level all the time
		trueProgress = info.isLevelMilestone() ? getTotalLevel() : getTotalXP();
		info.setTrueProgress(trueProgress);
		updateMilestoneProgress(milestoneId, trueProgress);
	}
}
