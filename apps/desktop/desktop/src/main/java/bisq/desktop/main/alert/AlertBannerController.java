/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.main.alert;

import bisq.bonded_roles.security_manager.alert.AlertService;
import bisq.bonded_roles.security_manager.alert.AlertType;
import bisq.bonded_roles.security_manager.alert.AuthorizedAlertData;
import bisq.common.observable.Pin;
import bisq.common.observable.collection.CollectionObserver;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.view.Controller;
import bisq.settings.SettingsService;
import javafx.collections.ListChangeListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.Optional;

@Slf4j
public class AlertBannerController implements Controller {
    private final AlertBannerModel model;
    @Getter
    private final AlertBannerView view;
    private final SettingsService settingsService;
    private final AlertService alertService;
    private final ListChangeListener<AuthorizedAlertData> listChangeListener;
    private Pin authorizedAlertDataSetPin;

    public AlertBannerController(SettingsService settingsService, AlertService alertService) {
        this.settingsService = settingsService;
        this.alertService = alertService;
        model = new AlertBannerModel();
        view = new AlertBannerView(model, this);

        model.getSortedList().setComparator(Comparator.comparing(AuthorizedAlertData::getAlertType).reversed());

        listChangeListener = change -> {
            change.next();
            if (change.wasAdded()) {
                AuthorizedAlertData newItem = model.getSortedList().get(0);
                AuthorizedAlertData displayed = model.getDisplayedAuthorizedAlertData();
                if (displayed == null || newItem.getAlertType().ordinal() >= displayed.getAlertType().ordinal()) {
                    add(newItem);
                }
            } else if (change.wasRemoved()) {
                change.getRemoved().stream()
                        .filter(e -> e.equals(model.getDisplayedAuthorizedAlertData()))
                        .findFirst()
                        .ifPresent(e -> handleRemove());
            }
        };
    }

    @Override
    public void onActivate() {
        updatePredicate();

        authorizedAlertDataSetPin = alertService.getAuthorizedAlertDataSet().addObserver(new CollectionObserver<>() {
            @Override
            public void add(AuthorizedAlertData authorizedAlertData) {
                if (authorizedAlertData == null) {
                    return;
                }
                UIThread.run(() -> model.getObservableList().add(authorizedAlertData));
            }

            @Override
            public void remove(Object element) {
                if (element instanceof AuthorizedAlertData) {
                    UIThread.run(() -> model.getObservableList().remove((AuthorizedAlertData) element));
                }
            }

            @Override
            public void clear() {
                UIThread.run(() -> model.getObservableList().clear());
            }
        });

        model.getSortedList().addListener(listChangeListener);
        model.getSortedList().stream().findFirst().ifPresent(this::add);
    }

    @Override
    public void onDeactivate() {
        authorizedAlertDataSetPin.unbind();
        model.getSortedList().removeListener(listChangeListener);
    }

    void onClose() {
        settingsService.getConsumedAlertIds().add(model.getDisplayedAuthorizedAlertData().getId());
        updatePredicate();
        handleRemove();
    }

    private void add(AuthorizedAlertData authorizedAlertData) {
        model.setDisplayedAuthorizedAlertData(authorizedAlertData);
        Optional<String> optionalMessage = authorizedAlertData.getMessage();

        if (optionalMessage.isPresent()) {
            log.info("Showing alert with message {}", optionalMessage.get());
            model.getHeadline().set(authorizedAlertData.getHeadline().orElseThrow());
            model.getMessage().set(authorizedAlertData.getMessage().orElseThrow());
            model.getAlertType().set(authorizedAlertData.getAlertType());
            model.getIsAlertVisible().set(true);
        } else {
            log.warn("optionalMessage not present");
            model.getIsAlertVisible().set(false);
        }
    }

    private void handleRemove() {
        model.reset();
        model.getSortedList().stream().findFirst().ifPresent(this::add);
    }

    private void updatePredicate() {
        model.getFilteredList().setPredicate(authorizedAlertData ->
                !settingsService.getConsumedAlertIds().contains(authorizedAlertData.getId()) &&
                        authorizedAlertData.getAlertType() != AlertType.BAN);
    }
}
