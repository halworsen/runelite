package net.runelite.client.plugins.milestones.skills;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.stream.IntStream;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

@Slf4j
public class SkillMilestoneEditPanel extends JPanel
{
	private SkillMilestoneManager manager;
	private Client client;
	private SkillIconManager iconManager;

	private final JPanel mainContainer = new JPanel();
	private final MaterialTabGroup tabGroup = new MaterialTabGroup();

	private JRadioButton levelButton;
	private JButton updateMilestoneButton;
	private FlatTextField targetInput;
	private Skill selectedSkill;

	SkillMilestoneEditPanel(SkillMilestoneManager manager, SkillIconManager iconManager, Client client)
	{
		this.manager = manager;
		this.iconManager = iconManager;
		this.client = client;

		init();
	}

	private void init()
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		mainContainer.setLayout(new GridBagLayout());
		mainContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		GridBagConstraints c = new GridBagConstraints();
		// Actually for margins
		c.insets = new Insets(0, 0, 10, 0);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridx = 0;
		c.gridy = 0;

		tabGroup.setLayout(new GridLayout(0, 6, 5, 5));
		addSkillButtons();

		updateMilestoneButton = new JButton();
		updateMilestoneButton.setText("Select a skill");
		updateMilestoneButton.addActionListener((event) -> updateSkillMilestone());
		updateMilestoneButton.setEnabled(false);

		mainContainer.add(tabGroup, c);
		c.gridy++;

		mainContainer.add(getRadioButtons(), c);
		c.gridy++;

		mainContainer.add(getTargetEntry(), c);
		c.gridy++;

		mainContainer.add(updateMilestoneButton, c);

		add(mainContainer, BorderLayout.NORTH);
	}

	// This creates/updates the skill milestones
	private void updateSkillMilestone()
	{
		int skillTarget = getSkillTarget(false);

		int milestoneId = manager.getMilestoneBySkill(selectedSkill);
		if (milestoneId == -1)
		{
			manager.addNewMilestone(
					selectedSkill,
					skillTarget,
					levelSelected()
			);
		}
		else
		{
			manager.updateMilestone(
					milestoneId,
					skillTarget,
					levelSelected()
			);
		}

		// Update the button
		setSelectedSkill(selectedSkill);
	}

	private void addSkillButtons()
	{
		for (Skill skill : Skill.values())
		{
			ImageIcon icon = new ImageIcon(iconManager.getSkillImage(skill, true));
			MaterialTab tab = new MaterialTab(icon, tabGroup, null);
			tab.setOnSelectEvent(() ->
			{
				setSelectedSkill(skill);
				return true;
			});

			tabGroup.addTab(tab);
		}
	}

	private void setSelectedSkill(Skill skill)
	{
		selectedSkill = skill;

		String buttonText = "Add milestone";

		int milestoneId = manager.getMilestoneBySkill(skill);
		if (milestoneId != -1)
		{
			buttonText = "Update milestone";

			int target = manager.getTrueTarget(milestoneId);
			if (levelSelected())
			{
				target = Experience.getLevelForXp(target);
			}
			targetInput.setText(Integer.toString(target));
		}

		updateMilestoneButton.setText(buttonText);
		updateMilestoneButton.setEnabled(true);
	}

	private int getSkillTarget(boolean raw)
	{
		try
		{
			int target = Integer.parseInt(targetInput.getText());

			if (raw)
			{
				return target;
			}

			if (levelSelected() && selectedSkill != Skill.OVERALL)
			{

				target = Experience.getXpForLevel(Math.max(1, target));
			}

			return target;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private void updateTargetEntry(FlatTextField field)
	{
		int maxTarget = levelSelected() ? Experience.MAX_VIRT_LEVEL : Experience.MAX_SKILL_XP;
		int minTarget = levelSelected() ? client.getRealSkillLevel(selectedSkill) : client.getSkillExperience(selectedSkill);

		if (selectedSkill == Skill.OVERALL)
		{
			// Would use the absolute max of 4,600,000,000, but that's larger than an int
			maxTarget = levelSelected() ? Experience.MAX_TOTAL_LEVEL : Experience.MAX_TOTAL_LEVEL_XP;
			minTarget = levelSelected() ? client.getTotalLevel() : manager.getTotalXP();
		}

		// Clamp the target
		int target = Math.min(maxTarget, Math.max(minTarget, getSkillTarget(true)));

		field.setText(Integer.toString(target));
	}

	// Admittedly taken from the skill calculator plugin
	private JPanel getTargetEntry()
	{
		final JPanel container = new JPanel();
		container.setLayout(new BorderLayout());

		final JLabel uiLabel = new JLabel("Target");
		targetInput = new FlatTextField();

		targetInput.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		targetInput.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		targetInput.setBorder(new EmptyBorder(5, 7, 5, 7));
		targetInput.addActionListener((event) -> updateTargetEntry(targetInput));

		uiLabel.setFont(FontManager.getRunescapeSmallFont());
		uiLabel.setBorder(new EmptyBorder(0, 0, 4, 0));
		uiLabel.setForeground(Color.WHITE);

		container.add(uiLabel, BorderLayout.NORTH);
		container.add(targetInput, BorderLayout.CENTER);

		return container;
	}

	private boolean levelSelected()
	{
		return levelButton.isSelected();
	}

	private JPanel getRadioButtons()
	{
		final JPanel container = new JPanel();
		container.setLayout(new BorderLayout());

		levelButton = new JRadioButton("Level", true);
		final JRadioButton xpButton = new JRadioButton("XP");

		levelButton.addActionListener((event) ->
		{
			levelButton.setSelected(true);
			xpButton.setSelected(false);
			updateTargetEntry(targetInput);
		});
		xpButton.addActionListener((event) ->
		{
			xpButton.setSelected(true);
			levelButton.setSelected(false);
			updateTargetEntry(targetInput);
		});

		container.add(levelButton, BorderLayout.PAGE_START);
		container.add(xpButton, BorderLayout.PAGE_END);

		return container;
	}
}
