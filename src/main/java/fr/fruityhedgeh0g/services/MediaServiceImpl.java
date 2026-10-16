package fr.fruityhedgeh0g.services;

import fr.fruityhedgeh0g.utilities.logging.Logged;

import fr.fruityhedgeh0g.dtos.mediaDtos.MediaDto;
import fr.fruityhedgeh0g.exceptions.NotImplementedYetException;
import fr.fruityhedgeh0g.exceptions.UnknownResourceException;
import fr.fruityhedgeh0g.repositories.MediaRepository;
import fr.fruityhedgeh0g.services.interfaces.MediaService;
import fr.fruityhedgeh0g.utilities.mappers.MediaMapper;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Logged
@ApplicationScoped
@Default
public class MediaServiceImpl implements MediaService {
    @Inject
    MediaRepository mediaRepository;

    @Inject
    MediaMapper mediaMapper;

    @Inject
    MediaUses mediaUses;

    @Override
    public List<MediaDto> listAll() {
        // The Medias outside the Galerie: a Photo de la Galerie is seen through the Galerie, as its Visibilité allows
        return mediaRepository.list("from MediaEntity m where not exists (select p from GalleryPhotoEntity p where p.media = m)")
                .stream()
                .map(media -> mediaMapper.toDto(media).withUsedBy(mediaUses.usedBy(media.getMediaId())))
                .toList();
    }

    @Override
    public MediaDto getById(UUID mediaId) {
        return mediaMapper.toDto(
                mediaRepository.findByIdOptional(mediaId)
                        .orElseThrow(() -> new UnknownResourceException("Media not found: "+mediaId))
        );

    }

    @Override
    @Transactional
    public MediaDto create(MediaDto mediaDto) {
        throw new NotImplementedYetException(this.getClass().getSimpleName());
    }

    @Override
    @Transactional
    public MediaDto update(MediaDto mediaDto) {

        throw new NotImplementedYetException(this.getClass().getSimpleName());
    }

    @Override
    @Transactional
    public void delete(UUID mediaId) {
        throw new NotImplementedYetException(this.getClass().getSimpleName());
    }

//    @Transactional
//    public Try<List<MediaDto>> getAllMedia(){
//        Log.info("Getting all media");
//        return Try.of(() -> mediaRepository
//                .findAll()
//                .stream()
//                .map(mediaMapper::toDto)
//                .toList())
//                .onFailure(e ->
//                        Log.error("Error getting all media", e)
//                );
//    }
//
//    @Override
//    @Transactional
//    public Try<MediaDto> getMediaById( UUID mediaId){
//        Log.infof("Getting media with id: %s", mediaId);
//        return Try.of(() -> mediaRepository
//                .findByIdOptional(mediaId)
//                .orElseThrow(() ->
//                        new UnknownResourceException("Media not found: " + mediaId)))
//                .map(mediaMapper::toDto)
//                .onFailure(e -> {
//                    if (e instanceof UnknownResourceException ex) {
//                        Log.warn(ex.getMessage());
//                    } else {
//                        Log.errorf(e, "Error getting media with id: %s", mediaId);
//                    }
//                });
//
//    }
//
//    @Override
//    @Transactional
//    public Try<MediaDto> createMedia( MediaDto mediaDto){
//        return null;
//    }
//
//    //TODO : Développer l'update
//    @Override
//    @Transactional
//    public Try<MediaDto> updateMedia( MediaDto mediaDto){
//        return null;
//    }
//
//    //TODO : Gérer la suppression des références sur les autres tables (Côté Entity)
//    @Override
//    @Transactional
//    public Try<Void> deleteMedia( UUID mediaId) {
//        return null;
//    }
}
