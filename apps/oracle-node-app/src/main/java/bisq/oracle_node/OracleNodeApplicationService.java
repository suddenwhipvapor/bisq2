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

package bisq.oracle_node;

import bisq.application.ApplicationService;
import bisq.bonded_roles.BondedRolesService;
import bisq.bonded_roles.market_price.MarketPriceRequestService;
import bisq.java_se.JvmMemoryReportService;
import bisq.common.platform.MemoryReportService;
import bisq.common.platform.PlatformUtils;
import bisq.identity.IdentityService;
import bisq.evolution.migration.MigrationService;
import bisq.java_se.guava.GuavaJavaSeFunctionProvider;
import bisq.network.NetworkService;
import bisq.network.NetworkServiceConfig;
import bisq.security.SecurityService;
import bisq.security.pow.equihash.Equihash;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.supplyAsync;

@Slf4j
@Getter
public class OracleNodeApplicationService extends ApplicationService {
    private final IdentityService identityService;
    private final SecurityService securityService;
    private final NetworkService networkService;
    private final OracleNodeService oracleNodeService;
    private final BondedRolesService bondedRolesService;
    private final MigrationService migrationService;
    private final MemoryReportService memoryReportService;
    
    public OracleNodeApplicationService(String[] args) {
        super("oracle_node", args, PlatformUtils.getUserDataDir());

        // Guava has different APIs for Java SE and Android.
        // To allow re-usability on Android we use the Android in Equihash. Here we use the Java SE version.
        Equihash.setGuavaFunctionProvider(new GuavaJavaSeFunctionProvider());

        migrationService = new MigrationService(getConfig().getBaseDir());

        memoryReportService = new JvmMemoryReportService(getConfig().getMemoryReportIntervalSec(), getConfig().isIncludeThreadListInMemoryReport());

        securityService = new SecurityService(persistenceService, SecurityService.Config.from(getConfig("security")));

        NetworkServiceConfig networkServiceConfig = NetworkServiceConfig.from(config.getBaseDir(),
                getConfig("network"));
        networkService = new NetworkService(networkServiceConfig,
                persistenceService,
                securityService.getKeyBundleService(),
                securityService.getHashCashProofOfWorkService(),
                securityService.getEquihashProofOfWorkService(),
                memoryReportService);

        identityService = new IdentityService(persistenceService,
                securityService.getKeyBundleService(),
                networkService
        );

        bondedRolesService = new BondedRolesService(BondedRolesService.Config.from(getConfig("bondedRoles")),
                persistenceService,
                networkService);

        com.typesafe.config.Config bondedRolesConfig = getConfig("bondedRoles");
        com.typesafe.config.Config marketPriceConfig = bondedRolesConfig.getConfig("marketPrice");
        MarketPriceRequestService marketPriceRequestService = new MarketPriceRequestService(
                MarketPriceRequestService.Config.from(marketPriceConfig),
                networkService);

        OracleNodeService.Config oracleNodeConfig = OracleNodeService.Config.from(getConfig("oracleNode"));
        oracleNodeService = new OracleNodeService(oracleNodeConfig,
                identityService,
                networkService,
                persistenceService,
                bondedRolesService.getAuthorizedBondedRolesService(),
                marketPriceRequestService,
                memoryReportService);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return migrationService.initialize()
                .thenCompose(result -> memoryReportService.initialize())
                .thenCompose(result -> securityService.initialize())
                .thenCompose(result -> networkService.initialize())
                .thenCompose(result -> identityService.initialize())
                .thenCompose(result -> bondedRolesService.initialize())
                .thenCompose(result -> oracleNodeService.initialize())
                .orTimeout(5, TimeUnit.MINUTES)
                .whenComplete((success, throwable) -> {
                    if (success) {
                        bondedRolesService.getDifficultyAdjustmentService().getMostRecentValueOrDefault().addObserver(mostRecentValueOrDefault -> networkService.getNetworkLoadServices().forEach(networkLoadService ->
                                networkLoadService.setDifficultyAdjustmentFactor(mostRecentValueOrDefault)));
                        log.info("NetworkApplicationService initialized");
                    } else {
                        log.error("Initializing networkApplicationService failed", throwable);
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> shutdown() {
        // We shut down services in opposite order as they are initialized
        return supplyAsync(() -> oracleNodeService.shutdown()
                .thenCompose(result -> bondedRolesService.shutdown())
                .thenCompose(result -> identityService.shutdown())
                .thenCompose(result -> networkService.shutdown())
                .thenCompose(result -> securityService.shutdown())
                .thenCompose(result -> memoryReportService.shutdown())
                .thenCompose(result -> migrationService.shutdown())
                .orTimeout(2, TimeUnit.MINUTES)
                .handle((result, throwable) -> throwable == null)
                .join());
    }
}