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

package bisq.desktop.primary.onboarding.initUserProfile;

import bisq.desktop.common.view.Model;
import javafx.beans.property.*;
import javafx.scene.image.Image;
import lombok.Getter;

import java.security.KeyPair;

@Getter
public class InitialUserProfileModel implements Model {
    final ObjectProperty<Image> roboHashNode = new SimpleObjectProperty<>();
    final StringProperty feedback = new SimpleStringProperty();
    final StringProperty profileId = new SimpleStringProperty();
    final StringProperty nickName = new SimpleStringProperty();
    final BooleanProperty createProfileButtonDisable = new SimpleBooleanProperty();

    KeyPair tempKeyPair = null;
    String tempKeyId;

    public InitialUserProfileModel() {
    }
}