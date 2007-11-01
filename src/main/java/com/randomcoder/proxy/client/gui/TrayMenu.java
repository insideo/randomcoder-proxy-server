package com.randomcoder.proxy.client.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;

import com.randomcoder.systray.*;

/**
 * Tray icon implementation for HTTP proxy.
 * 
 * <pre>
 * Copyright (c) 2007, Craig Condit. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *     
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS &quot;AS IS&quot;
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * </pre>
 */
public class TrayMenu
{
	private final boolean supported;
	private final JFrame mainWindow;
	private final JFrame aboutWindow;
	private final JFrame prefsWindow;
	private final TrayIconWrapper icon;
	private final LinkedList<ActionListener> closeListeners = new LinkedList<ActionListener>();
	
	private boolean visible = false;
	
	/**
	 * Creates a new, invisible tray icon implementation.
	 * 
	 * @param mainWindow
	 *            Default window to display
	 * @param aboutWindow
	 *            About window to display
	 * @param prefsWindow
	 *            Preferences window to display
	 */
	public TrayMenu(final JFrame mainWindow, final JFrame aboutWindow, final JFrame prefsWindow)
	{		
		this.mainWindow = mainWindow;
		this.aboutWindow = aboutWindow;
		this.prefsWindow = prefsWindow;
		
		supported = SystemTrayWrapper.isSupported();
		
		if (supported)
		{
			// system tray available
			SystemTrayWrapper tray = SystemTrayWrapper.getSystemTray();
			
			Dimension size = tray.getTrayIconSize();
			int w = (int) size.getWidth();
			
			String filename = "/tray-icon-256x256.png";
			if (w <= 16)
				filename = "/tray-icon-16x16.png";
			else if (w <= 32)
				filename = "/tray-icon-32x32.png";
			else if (w <= 64)
				filename = "/tray-icon-64x64.png";
			else if (w <= 128)
				filename = "/tray-icon-128x128.png";
			
			ImageIcon trayImage = new ImageIcon(getClass().getResource(filename));
			
			PopupMenu popup = new PopupMenu();
			
			popup.setFont(new JMenuItem("test").getFont());
			
			MenuItem open = new MenuItem("Open");
			open.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (mainWindow != null)
					{
						mainWindow.setVisible(true);
						mainWindow.requestFocus();
					}
				}
			});				
			open.setFont(popup.getFont().deriveFont(Font.BOLD));
			popup.add(open);

			MenuItem configure = new MenuItem("Preferences");
			configure.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (prefsWindow != null)
					{
						prefsWindow.setVisible(true);
						prefsWindow.requestFocus();
					}
				}
			});				
			popup.add(configure);
			
			popup.addSeparator();
			
			MenuItem about = new MenuItem("About");
			about.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (aboutWindow != null)
					{
						aboutWindow.setVisible(true);
						aboutWindow.requestFocus();
					}
				}
			});
			popup.add(about);
			
			popup.addSeparator();
			
			MenuItem exit = new MenuItem("Exit");
			exit.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					setVisible(false);
				}
			});
			popup.add(exit);
			
			icon = new TrayIconWrapper(trayImage.getImage(), "Disconnected", popup);
			icon.addActionListener(new ActionListener()
			{
				public void actionPerformed(ActionEvent e)
				{
					if (mainWindow != null)
					{
						mainWindow.setVisible(true);
						mainWindow.requestFocus();
					}
				}
			});
			icon.setImageAutoSize(true);			
		}
		else
		{
			icon = null;
		}
	}
	
	/**
	 * Determines if the tray icon functionality is configured.
	 * 
	 * @return <code>true</code> if tray icon is supported
	 */
	public boolean isSupported()
	{
		return supported;
	}
	
	/**
	 * Sets the visibility of the tray icon.
	 * 
	 * @param visible
	 *            <code>true</code> to display, <code>false</code> to hide
	 */
	public void setVisible(boolean visible)
	{
		if (visible)
		{
			// open
			try
			{
				if (icon != null && !this.visible)
					SystemTrayWrapper.getSystemTray().add(icon);
			}
			catch (AWTException ignored)
			{					
			}
		}		
		else
		{
			// notify quit listeners
			for (ActionListener listener : closeListeners)
				listener.actionPerformed(new ActionEvent(this, 0, "close"));
			
			// close
			if (icon != null && this.visible)
				SystemTrayWrapper.getSystemTray().remove(icon);
			
			// exit
			System.exit(0);
		}
		this.visible = visible;
	}
	
	/**
	 * Adds the given listener to the list of handlers notified when close is
	 * chosen.
	 * 
	 * @param listener
	 *            action listener to be notified on close
	 */
	public void addCloseListener(ActionListener listener)
	{
		for (Iterator<ActionListener> it = closeListeners.iterator(); it.hasNext();)
			if (listener == it.next())
				return;
		
		closeListeners.addLast(listener);	
	}
	
	/**
	 * Removes the given listener from the list of handlers notified when close
	 * is chosen.
	 * 
	 * @param listener
	 *            action listener to be removed
	 */
	public void removeCloseListener(ActionListener listener)
	{
		for (Iterator<ActionListener> it = closeListeners.iterator(); it.hasNext();)
			if (listener == it.next())
				it.remove();				
	}
	
	/**
	 * UI test endpoint.
	 * 
	 * @param args
	 *            unused
	 */
	public static void main(String[] args)
	{
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			
			TrayMenu menu = new TrayMenu(null, null, null);
			menu.setVisible(true);
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}
}