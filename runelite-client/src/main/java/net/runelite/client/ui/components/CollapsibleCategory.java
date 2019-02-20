package net.runelite.client.ui.components;

import lombok.Getter;
import lombok.Setter;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * This panel holds another panel. Clicking its header will show/hide the held panel
 */
public class CollapsibleCategory extends JPanel
{

	private ImageIcon openIcon;
	private ImageIcon collapseIcon;

	private final BorderLayout layout = new BorderLayout();
	private final JPanel headerPanel = new JPanel();
	private final JLabel titleLabel = new JLabel();
	private final JLabel collapseButton = new JLabel();

	@Setter
	@Getter
	private String title;
	@Setter
	@Getter
	private JPanel heldPanel;
	@Getter
	private boolean isOpen = false;

	public CollapsibleCategory(JPanel panel)
	{
		init("", panel);
	}

	public CollapsibleCategory(String title, JPanel panel)
	{
		init(title, panel);
	}

	private void init(String title, JPanel panel)
	{
		openIcon = new ImageIcon(this.getClass().getResource("open.png"));
		collapseIcon = new ImageIcon(this.getClass().getResource("collapse.png"));

		this.title = title;
		this.heldPanel = panel;

		setLayout(layout);

		headerPanel.setLayout(new BorderLayout());
		headerPanel.setBorder(new EmptyBorder(7, 7, 7, 7));
		headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		titleLabel.setText(title);

		collapseButton.setIcon(openIcon);

		// MouseListener for opening/collapsing the panel
		headerPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				toggleOpen();
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				headerPanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		headerPanel.add(titleLabel, BorderLayout.LINE_START);
		headerPanel.add(collapseButton, BorderLayout.LINE_END);

		add(headerPanel, BorderLayout.PAGE_START);
	}

	private void toggleOpen()
	{
		isOpen = !isOpen;

		if (isOpen)
		{
			layout.setVgap(5);
			add(heldPanel, BorderLayout.CENTER);

			collapseButton.setIcon(collapseIcon);
		}
		else
		{
			layout.setVgap(0);
			remove(heldPanel);

			collapseButton.setIcon(openIcon);
		}

		revalidate();
		repaint();
	}

	public void setTitleFont(Font newFont)
	{
		titleLabel.setFont(newFont);
	}

	public void setBorderSize(int width)
	{
		headerPanel.setBorder(new EmptyBorder(width, width, width, width));
	}

}
