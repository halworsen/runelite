package net.runelite.client.plugins.milestones.skills;

import lombok.Data;
import net.runelite.api.Skill;

@Data
public class SkillMilestoneInfo
{
	private Skill skill;
	private boolean isLevelMilestone;
	private int trueProgress;
	private int trueTarget;
}
