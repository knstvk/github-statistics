package com.company.ghstat.web.screens.contributor;

import com.company.ghstat.entity.Contributor;
import com.company.ghstat.entity.Repository;
import com.company.ghstat.entity.UserInfo;
import com.company.ghstat.service.GithubInfoService;
import com.company.ghstat.web.screens.userinfo.UserInfoView;
import com.haulmont.cuba.core.entity.KeyValueEntity;
import com.haulmont.cuba.core.global.DataLoadContext;
import com.haulmont.cuba.core.global.DataManager;
import com.haulmont.cuba.core.global.LoadContext;
import com.haulmont.cuba.core.global.ValueLoadContext;
import com.haulmont.cuba.gui.Notifications;
import com.haulmont.cuba.gui.ScreenBuilders;
import com.haulmont.cuba.gui.UiComponents;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.model.CollectionLoader;
import com.haulmont.cuba.gui.model.KeyValueCollectionLoader;
import com.haulmont.cuba.gui.screen.*;
import com.haulmont.cuba.gui.screen.LookupComponent;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * {@link Contributor} browse screen
 */

@UiController("ghstat_Contributor.browse")
@UiDescriptor("contributor-browse.xml")
@LookupComponent("contributorsTable")
public class ContributorBrowse extends StandardLookup<Contributor> {
    @Inject
    private GithubInfoService githubInfoService;
    @Inject
    private Logger log;
    @Inject
    private Notifications notifications;
    @Inject
    private TextField<String> repoOwnerField;
    @Inject
    private LookupField<Repository> repoNameField;
    @Inject
    private KeyValueCollectionLoader contributorsDl;
    @Inject
    private CollectionLoader<Repository> reposDl;
    @Inject
    private UiComponents uiComponents;
    @Inject
    private ScreenBuilders screenBuilders;
    @Inject
    private DataManager dataManager;

    /**
     * Loads a list of {@link Repository} entity for the {@code reposDc} data container.
     * The result is desc sorted by the count of repository stars.
     *
     * @param loadContext parameter is not used in the implementation
     * @return Returns a list of {@link Repository} for a GitHub owner, provided by {@code repoOwnerField}
     */
    @Install(to = "reposDl", target = Target.DATA_LOADER)
    private List<Repository> reposDlLoadDelegate(LoadContext<Repository> loadContext) {
        repoNameField.setValue(null);
        if (repoOwnerField.getValue() == null)
            return new ArrayList<>();
        try {
            List<Repository> repos = githubInfoService.getRepos(repoOwnerField.getValue());
            repos.sort((o1, o2) -> o2.getStargazersCount().compareTo(o1.getStargazersCount()));
            return repos;
        } catch (Exception e) {
            handleExceptionWithNotification(e);
            return new ArrayList<>();
        }
    }

    /**
     * Sets the most starred {@link Repository} into {@code repoNameField} drop-down list
     */
    @Subscribe(id = "reposDl", target = Target.DATA_LOADER)
    public void onReposDlPostLoad(CollectionLoader.PostLoadEvent<Repository> event) {
        if (event.getLoadedEntities().size() > 0)
            repoNameField.setValue(event.getLoadedEntities().get(0));
    }

    /**
     * Loads a list of {@link Contributor} entity for the {@code contributorsDc} data container.
     * The result is desc sorted by the count of contributions.
     * Supports pagination.
     *
     * @param valueLoadContext parameter is not used in the implementation
     * @return Returns a list of {@link Contributor} for a GitHub repository, provided by {@code repoNameField}
     */
    @Install(to = "contributorsDl", target = Target.DATA_LOADER)
    private List<KeyValueEntity> contributorsDlLoadDelegate(ValueLoadContext valueLoadContext) {
        if (repoNameField.getValue() == null)
            return new ArrayList<>();
        try {
            int page = contributorsDl.getFirstResult() / contributorsDl.getMaxResults() + 1;
            List<Contributor> contributors = githubInfoService.getContributors(repoNameField.getValue(), page, contributorsDl.getMaxResults());
            return contributors.stream()
                    .map(contributor -> {
                        KeyValueEntity keyValueEntity = new KeyValueEntity();
                        keyValueEntity.setValue("login", contributor.getLogin());
                        keyValueEntity.setValue("contributions", contributor.getContributions());
                        return keyValueEntity;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            handleExceptionWithNotification(e);
            return new ArrayList<>();
        }
    }

    /**
     * Returns a total number of rows which is shown when the user clicks the "?" link in the paging control.
     */
    @Install(to = "contributorsTable", subject = "rowsCountTotalCountDelegate")
    private Long contributorsTableRowsCountTotalCountDelegate(DataLoadContext dataLoadContext) {
        // get the total count from an external service
        return 0L;
    }

    /**
     * Triggers GitHub repositories load right after {@code repoOwnerField} is changed
     */
    @Subscribe("repoOwnerField")
    public void onRepoOwnerFieldValueChange(HasValue.ValueChangeEvent<String> event) {
        reposDl.load();
    }

    /**
     * Triggers GitHub contributors load right after {@code repoNameField} is changed
     */
    @Subscribe("repoNameField")
    public void onRepoNameFieldValueChange(HasValue.ValueChangeEvent<Repository> event) {
        contributorsDl.load();
    }

    /**
     * Generates a column with {@link LinkButton}.
     * It opens {@code UserInfoView} screen, providing {@link UserInfo} entity as an editing entity.
     */
    @Install(to = "contributorsTable.login", subject = "columnGenerator")
    private Component contributorsTableLoginColumnGenerator(KeyValueEntity contributor) {
        LinkButton linkButton = uiComponents.create(LinkButton.class);
        linkButton.setCaption(contributor.getValue("login"));
        linkButton.addClickListener(event -> {
            UserInfoView userInfoScreen = screenBuilders.screen(this)
                    .withScreenClass(UserInfoView.class)
                    .withOpenMode(OpenMode.DIALOG)
                    .build();
            userInfoScreen.setUserLoginToShow(contributor.getValue("login"));
            userInfoScreen.show();
        });
        return linkButton;
    }

    private void handleExceptionWithNotification(Exception e) {
        log.error(e.getMessage());
        notifications.create(Notifications.NotificationType.ERROR)
                .withPosition(Notifications.Position.BOTTOM_RIGHT)
                .withCaption(e.getMessage())
                .show();
    }

}