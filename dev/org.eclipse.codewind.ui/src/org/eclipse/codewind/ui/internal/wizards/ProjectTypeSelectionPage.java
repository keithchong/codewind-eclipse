/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.codewind.ui.internal.wizards;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.codewind.core.internal.InstallUtil;
import org.eclipse.codewind.core.internal.Logger;
import org.eclipse.codewind.core.internal.connection.CodewindConnection;
import org.eclipse.codewind.core.internal.connection.ProjectTemplateInfo;
import org.eclipse.codewind.core.internal.connection.RepositoryInfo;
import org.eclipse.codewind.core.internal.constants.ProjectInfo;
import org.eclipse.codewind.core.internal.constants.ProjectLanguage;
import org.eclipse.codewind.core.internal.constants.ProjectType;
import org.eclipse.codewind.ui.internal.messages.Messages;
import org.eclipse.codewind.ui.internal.prefs.RepositoryManagementDialog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;

public class ProjectTypeSelectionPage extends WizardPage {

	private CodewindConnection connection = null;
	private IPath projectPath = null;
	private Map<String, Set<String>> typeMap;
	private String type = null;
	private String language = null;
	private Text subtypeLabel = null;
	private CheckboxTableViewer subtypeViewer = null;
	private Text typeLabel = null;
	private CheckboxTableViewer typeViewer = null;
	private ProjectInfo projectInfo = null;

	protected ProjectTypeSelectionPage(CodewindConnection connection, IPath projectPath) {
		super(Messages.SelectProjectTypePageName);
		setTitle(Messages.SelectProjectTypePageTitle);
		setDescription(Messages.SelectProjectTypePageDescription);
		this.connection = connection;
		setProjectPath(projectPath);
		this.typeMap = getProjectTypeMap();
	}

	@Override
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.horizontalSpacing = 5;
		layout.verticalSpacing = 7;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		if (typeMap == null) {
			Text errorLabel = new Text(composite, SWT.READ_ONLY | SWT.WRAP);
			errorLabel.setText(Messages.SelectProjectTypeErrorLabel);
			setControl(composite);
			return;
		}
		if (typeMap.isEmpty()) {
			setErrorMessage(Messages.SelectProjectTypeNoProjectTypes);
		}
		
		typeLabel = new Text(composite, SWT.READ_ONLY);
		typeLabel.setText(Messages.SelectProjectTypePageProjectTypeLabel);
		typeLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false));
		typeLabel.setBackground(composite.getBackground());
		typeLabel.setForeground(composite.getForeground());
		
		typeViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		typeViewer.setContentProvider(ArrayContentProvider.getInstance());
		typeViewer.setLabelProvider(new ProjectTypeLabelProvider());
		typeViewer.setInput(getProjectTypeArray());
		GridData typeViewerData = new GridData(GridData.FILL, GridData.FILL, true, true);
		typeViewerData.minimumHeight = 200;
		typeViewer.getTable().setLayoutData(typeViewerData);
	   
		subtypeLabel = new Text(composite, SWT.READ_ONLY);
		subtypeLabel.setText(Messages.SelectProjectTypePageLanguageLabel);
		subtypeLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, false));
		subtypeLabel.setBackground(composite.getBackground());
		subtypeLabel.setForeground(composite.getForeground());
		
		subtypeViewer = CheckboxTableViewer.newCheckList(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		subtypeViewer.setContentProvider(ArrayContentProvider.getInstance());
		subtypeViewer.setLabelProvider(new ProjectSubtypeLabelProvider());
		subtypeViewer.getTable().setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));
		
		typeViewer.addCheckStateListener(new ICheckStateListener() {
			@Override
			public void checkStateChanged(CheckStateChangedEvent event) {
				if (event.getChecked()) {
					typeViewer.setCheckedElements(new Object[] {event.getElement()});
					type = (String) event.getElement();
				} else {
					type = null;
				}
				
				String[] languages = getLanguageArray(type);
				if (languages != null && languages.length > 1) {
					if (language != null) {
						boolean found = false;
						for (String lang : languages) {
							if (language.equals(lang)) {
								subtypeViewer.setCheckedElements(new Object[] {language});
								found = true;
								break;
							}
						}
						if (!found) {
							language = null;
						}
					}
					subtypeLabel.setVisible(true);
					subtypeViewer.setInput(languages);
					subtypeViewer.getTable().setVisible(true);
				} else {
					if (languages.length == 1) {
						language = languages[0];
					} else {
						language = null;
					}
					subtypeLabel.setVisible(false);
					subtypeViewer.getTable().setVisible(false);
				}
				getWizard().getContainer().updateButtons();
			}
		});

		subtypeViewer.addCheckStateListener(new ICheckStateListener() {
			@Override
			public void checkStateChanged(CheckStateChangedEvent event) {
				if (event.getChecked()) {
					subtypeViewer.setCheckedElements(new Object[] {event.getElement()});
					language = (String) event.getElement();
				} else {
					language = null;
				}
				getWizard().getContainer().updateButtons();
			}
		});
		
		// Manage repositories link
		Composite manageReposComp = new Composite(composite, SWT.NONE);
		manageReposComp.setLayout(new GridLayout(2, false));
		manageReposComp.setLayoutData(new GridData(GridData.END, GridData.FILL, false, false, 1, 1));
		
		Label manageRepoLabel = new Label(manageReposComp, SWT.NONE);
		manageRepoLabel.setText(Messages.SelectProjectTypeManageRepoLabel);
		manageRepoLabel.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));
		
		Link manageRepoLink = new Link(manageReposComp, SWT.NONE);
		manageRepoLink.setText("<a>" + Messages.SelectProjectTypeManageRepoLink + "</a>");
		manageRepoLink.setToolTipText(Messages.SelectProjectTypeManageRepoTooltip);
		manageRepoLink.setLayoutData(new GridData(GridData.END, GridData.CENTER, false, false));

		manageRepoLink.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				List<RepositoryInfo> repoList;
				try {
					repoList = connection.requestRepositories();
					RepositoryManagementDialog repoDialog = new RepositoryManagementDialog(getShell(), connection, repoList);
					if (repoDialog.open() == Window.OK) {
						if (repoDialog.hasChanges()) {
							IRunnableWithProgress runnable = new IRunnableWithProgress() {
								@Override
								public void run(IProgressMonitor monitor) throws InvocationTargetException {
									SubMonitor mon = SubMonitor.convert(monitor, Messages.RepoUpdateTask, 100);
									IStatus status = repoDialog.updateRepos(mon.split(75));
									if (!status.isOK()) {
										throw new InvocationTargetException(status.getException(), status.getMessage());
									}
									if (mon.isCanceled()) {
										return;
									}
									try {
										mon = mon.split(25);
										mon.setTaskName(Messages.SelectProjectTypeRefreshTypesTask);
										typeMap = getProjectTypeMap();
										mon.worked(25);
									} catch (Exception e) {
										throw new InvocationTargetException(e, Messages.SelectProjectTypeRefreshTypesError);
									}
								}
							};
							try {
								getWizard().getContainer().run(true, true, runnable);
							} catch (InvocationTargetException e) {
								MessageDialog.openError(getShell(), Messages.RepoUpdateErrorTitle, e.getMessage());
								return;
							} catch (InterruptedException e) {
								// The user cancelled the operation
								return;
							}
							updateTables();
						}
					}
				} catch (Exception e) {
					MessageDialog.openError(getShell(), Messages.RepoListErrorTitle, NLS.bind(Messages.RepoListErrorMsg, e));
				}
			}
		});
 
		subtypeLabel.setVisible(false);
		subtypeViewer.getTable().setVisible(false);

		updateTables();

		typeViewer.getTable().setFocus();
		setControl(composite);
	}

	public boolean canFinish() {
		if (type == null) {
			return false;
		}
		return true;
	}
	
	private String[] getProjectTypeArray() {
		Set<String> typeSet = typeMap.keySet();
		String[] types = typeSet.toArray(new String[typeSet.size()]);
		Arrays.sort(types, new Comparator<String>() {
			@Override
			public int compare(String t1, String t2) {
				return ProjectType.getDisplayName(t1).compareToIgnoreCase(ProjectType.getDisplayName(t2));
			}
		});
		return types;
	}
	
	private String[] getLanguageArray(String type) {
		Set<String> languageSet = typeMap.get(type);
		if (languageSet == null || languageSet.isEmpty()) {
			return new String[0];
		}
		String[] languages = languageSet.toArray(new String[languageSet.size()]);
		Arrays.sort(languages, new Comparator<String>() {
			@Override
			public int compare(String l1, String l2) {
				return ProjectLanguage.getDisplayName(l1).compareToIgnoreCase(ProjectLanguage.getDisplayName(l2));
			}
		});
		return languages;
	}
	
	private class ProjectTypeLabelProvider extends LabelProvider {

		@Override
		public Image getImage(Object element) {
			return null;
		}

		@Override
		public String getText(Object element) {
			return ProjectType.getDisplayName((String)element);
		}
		
	}

	private class ProjectSubtypeLabelProvider extends LabelProvider {

		@Override
		public Image getImage(Object element) {
			return null;
		}

		@Override
		public String getText(Object element) {
			return ProjectLanguage.getDisplayName((String)element);
		}
	}

	public void setProjectPath(IPath projectPath) {
		this.projectPath = projectPath;
		this.projectInfo = null;
		if (projectPath == null) {
			return;
		}
		if (getWizard() != null && getWizard().getContainer() != null) {
			IRunnableWithProgress runnable = new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor monitor) throws InvocationTargetException {
					SubMonitor mon = SubMonitor.convert(monitor, NLS.bind(Messages.SelectProjectTypeValidateTask, projectPath.lastSegment()), 100);
					projectInfo = getProjectInfo(mon.split(100));
				}
			};
			try {
				getContainer().run(true, true, runnable);
			} catch (InvocationTargetException e) {
				Logger.logError("An error occurred getting the project info for: " + projectPath.lastSegment(), e);
				return;
			} catch (InterruptedException e) {
				// The user cancelled the operation
				return;
			}
		} else {
			projectInfo = getProjectInfo(new NullProgressMonitor());
		}
		updateTables();
	}
	
	public CodewindConnection getConnection() {
		return connection;
	}
	
	public String getType() {
		// Type should not be null since the page cannot finish until a type is selected
		if (type == null) {
			Logger.logError("The project type is null on the project type selection page");
			return ProjectType.TYPE_UNKNOWN.getId();
		}
		return type;
	}
	
	public String getLanguage() {
		if (language == null) {
			// The language is optional so this is not an error
			return ProjectLanguage.LANGUAGE_UNKNOWN.getId();
		}
		return language;
	}

	private void updateTables() {
		if (typeViewer == null || typeViewer.getTable().isDisposed()) {
			return;
		}
		String[] projectTypes = getProjectTypeArray();
		typeViewer.setInput(projectTypes);
		if (projectTypes.length == 0) {
			setErrorMessage(Messages.SelectProjectTypeNoProjectTypes);
			updateLanguages(null, null);
			return;
		}
		setErrorMessage(null);
		if (type != null && typeMap.containsKey(type)) {
			// Maintain the current selection
			typeViewer.setCheckedElements(new Object[] {type});
			String[] languages = getLanguageArray(type);
			updateLanguages(languages, language);
		} else {
			// If no selection, use the project info
			if (projectInfo != null) {
				type = projectInfo.type.getId();
				language = projectInfo.language.getId();
				if (typeMap.containsKey(type)) {
					typeViewer.setCheckedElements(new Object[] {type});
					String[] languages = getLanguageArray(type);
					updateLanguages(languages, language);
				}
			}
		}
	}
	
	private void updateLanguages(String[] languages, String language) {
		if (subtypeViewer == null || subtypeViewer.getTable().isDisposed()) {
			return;
		}
		if (languages != null && languages.length > 1) {
			subtypeLabel.setVisible(true);
			subtypeViewer.setInput(languages);
			subtypeViewer.getTable().setVisible(true);
			if (language != null) {
				for (String lang : languages) {
					if (language.equals(lang)) {
						subtypeViewer.setCheckedElements(new Object[] {language});
						break;
					}
				}
			}
			subtypeViewer.getTable().setVisible(true);
		} else {
			subtypeLabel.setVisible(false);
			subtypeViewer.getTable().setVisible(false);
		}
	}

	private ProjectInfo getProjectInfo(IProgressMonitor monitor) {
		if (connection == null || projectPath == null) {
			return null;
		}

		try {
			return InstallUtil.validateProject(projectPath.lastSegment(), projectPath.toFile().getAbsolutePath(), monitor);
		} catch (Exception e) {
			Logger.logError("An error occurred trying to get the project type for project: " + projectPath.lastSegment(), e); //$NON-NLS-1$
		}

		return null;
	}

	private Map<String, Set<String>> getProjectTypeMap() {
		List<ProjectTemplateInfo> templates = null;
		Map<String, Set<String>> typeMap = new HashMap<String, Set<String>>();
		try {
			templates = connection.requestProjectTemplates(true);
		} catch (Exception e) {
			Logger.logError("An error occurred trying to get the list of templates for connection: " + connection.baseUrl, e); //$NON-NLS-1$
			return null;
		}
		if (templates == null || templates.isEmpty()) {
			Logger.log("The list of templates is empty for connection: " + connection.baseUrl); //$NON-NLS-1$
			return typeMap;
		}
		for (ProjectTemplateInfo template : templates) {
			Set<String> languages = typeMap.get(template.getProjectType());
			if (languages == null) {
				languages = new HashSet<String>();
				typeMap.put(template.getProjectType(), languages);
			}
			if (template.getLanguage() != null && !template.getLanguage().isEmpty()) {
				languages.add(template.getLanguage());
			}
		}
		return typeMap;
	}

}
