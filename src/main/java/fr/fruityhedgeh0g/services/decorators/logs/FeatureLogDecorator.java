package fr.fruityhedgeh0g.services.decorators.logs;

import fr.fruityhedgeh0g.dtos.featureDtos.FeatureDto;
import fr.fruityhedgeh0g.dtos.featureDtos.FeatureSwitchEntryDto;
import fr.fruityhedgeh0g.exceptions.KeycloakUnavailableException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.services.interfaces.FeatureService;
import io.quarkus.logging.Log;
import io.vavr.control.Try;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@Priority(200)
@Decorator
public class FeatureLogDecorator implements FeatureService{

    @Inject
    @Delegate
    FeatureService featureService;

    @Override
    public List<FeatureDto> listAll() {
        Log.debugf("Retrieving all features...");
        return Try.of(featureService::listAll)
                .onSuccess(features -> Log.debugf("%d features retrieved.",features.size()))
                .onFailure(t -> Log.errorf(t,"An error occurred while retrieving features."))
                .get();
    }

    @Override
    public FeatureDto getByName(String name) {
        Log.debugf("Retrieving feature by name %s...",name);
        return Try.of(() -> featureService.getByName(name))
                .onSuccess(feature -> {
                    Log.debugf("Feature retrieved: "+feature.toString());
                })
                .onFailure(t -> {
                    switch(t){
                        case UnknownResourceException ex -> Log.errorf(ex, "Feature not found: %s", name);
                        default -> Log.errorf(t,"An error occurred while retrieving feature.");
                    }
                })
                .get();
    }

    @Override
    public FeatureDto switchLever(String name, boolean active, String reason, UUID by) {
        Log.infof("Switching the Feature %s %s for the whole site, asked by %s...", name, active ? "on" : "off", by);
        return Try.of(() -> featureService.switchLever(name, active, reason, by))
                .onSuccess(feature -> Log.infof("Feature %s is now %s.", name, active ? "on" : "off"))
                .onFailure(t -> {
                    switch(t){
                        case UnknownResourceException ex -> Log.errorf(ex,"Feature %s not found.", name);
                        case KeycloakUnavailableException ex -> Log.errorf(ex,"Feature %s left as it was: Keycloak could not be changed.", name);
                        default -> Log.errorf(t,"An error occurred while switching the Feature %s.", name);
                    }
                })
                .get();
    }

    @Override
    public List<FeatureSwitchEntryDto> journal(int page, int size) {
        Log.debugf("Retrieving page %d of the Journal...", page);
        return Try.of(() -> featureService.journal(page, size))
                .onSuccess(entries -> Log.debugf("%d Journal entries retrieved.", entries.size()))
                .onFailure(t -> Log.errorf(t,"An error occurred while retrieving the Journal."))
                .get();
    }
}
