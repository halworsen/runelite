package net.runelite.client.plugins.milestones.skills;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.AsyncBufferedImage;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.milestones.MilestonesCategoryManager;
import net.runelite.client.plugins.milestones.MilestonesPlugin;

// TODO: track the progress towards skill goals
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

	private Map<Integer, Skill> milestoneSkillMap = new HashMap<>();
	// Holds the XP progress/goal for the milestones
	private Map<Integer, Map.Entry<Integer, Integer>> trueMilestones = new HashMap<>();

	SkillMilestoneManager()
	{
		this.categoryName = "Skills";
		this.categoryMilestones = new HashMap<>();
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
		Skill skill = milestoneSkillMap.get(milestoneId);
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

	protected int getRealTarget(int milestoneId)
	{
		return trueMilestones.get(milestoneId).getValue();
	}

	protected int getMilestoneBySkill(Skill skill)
	{
		if (!hasMilestoneForSkill(skill))
		{
			return -1;
		}

		for (Map.Entry<Integer, Skill> entry : milestoneSkillMap.entrySet())
		{
			if (entry.getValue() == skill)
			{
				return entry.getKey();
			}
		}

		return -1;
	}

	protected boolean hasMilestoneForSkill(Skill skill)
	{
		return milestoneSkillMap.containsValue(skill);
	}

	private String getMilestoneName(Skill skill, boolean isLevelMilestone)
	{
		return skill.getName() + " " + (isLevelMilestone ? "level" : "XP");
	}

	protected int addNewMilestone(Skill skill, int amount, boolean isLevelMilestone)
	{
		int realProgress = client.getSkillExperience(skill);
		int milestoneProgress = realProgress;
		int milestoneAmount = amount;

		if (isLevelMilestone)
		{
			milestoneProgress = Experience.getLevelForXp(realProgress);
			milestoneAmount = Experience.getLevelForXp(amount);
		}

		String milestoneName = getMilestoneName(skill, isLevelMilestone);
		int milestoneId = addNewMilestone(milestoneName, milestoneProgress, milestoneAmount);

		// Store the milestone XP progress/goal
		Map.Entry<Integer, Integer> trueMilestone = new AbstractMap.SimpleEntry<>(realProgress, amount);
		trueMilestones.put(milestoneId, trueMilestone);

		milestoneSkillMap.put(milestoneId, skill);

		return milestoneId;
	}

	protected void updateMilestone(int milestoneId, int amount, boolean isLevelMilestone)
	{
		if (!containsMilestone(milestoneId))
		{
			throw new IllegalStateException("Attempt to update milestone not belonging by the skills category");
		}

		Skill skill = milestoneSkillMap.get(milestoneId);

		int realProgress = client.getSkillExperience(skill);
		int milestoneProgress = realProgress;
		int milestoneAmount = amount;
		if (isLevelMilestone)
		{
			milestoneProgress = Experience.getLevelForXp(realProgress);
			milestoneAmount = Experience.getLevelForXp(amount);
		}

		Map.Entry<Integer, Integer> trueMilestone = new AbstractMap.SimpleEntry<>(realProgress, amount);
		trueMilestones.put(milestoneId, trueMilestone);

		String milestoneName = getMilestoneName(skill, isLevelMilestone);
		updateMilestone(milestoneId, milestoneName, milestoneProgress, milestoneAmount);
	}

	@Override
	protected void removeMilestone(int milestoneId)
	{
		super.removeMilestone(milestoneId);

		milestoneSkillMap.remove(milestoneId);
	}

	public String getSaveData()
	{
		final Gson gson = new Gson();

		Map<String, Map> milestoneMaps = new HashMap<>();
		milestoneMaps.put("skills", milestoneSkillMap);
		milestoneMaps.put("truemilestones", trueMilestones);

		return gson.toJson(milestoneMaps);
	}

	public void loadSaveData(String data)
	{
		milestoneSkillMap = new HashMap<>();
		trueMilestones = new HashMap<>();

		final Gson gson = new Gson();

		Map<String, Map> storedMaps = gson.fromJson(
				data,
				new TypeToken<Map<String, Map>>()
				{ }.getType()
		);

		Map<String, String> skillsData = storedMaps.get("skills");
		for (Map.Entry<String, String> entry : skillsData.entrySet())
		{
			milestoneSkillMap.put(Integer.parseInt(entry.getKey()), Skill.valueOf(entry.getValue()));
		}
		trueMilestones = storedMaps.get("truemilestones");
	}

}
