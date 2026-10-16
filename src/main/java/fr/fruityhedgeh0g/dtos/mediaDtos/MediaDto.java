package fr.fruityhedgeh0g.dtos.mediaDtos;

import java.util.List;
import lombok.With;
import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.Views;
import lombok.Value;

import java.util.UUID;

@Value
public class MediaDto {

    @JsonView({Views.Minimal.class,Views.CreationResponse.class,Views.UpdateResponse.class})
    UUID mediaId;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String fileKey;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String originalFilename;

    @JsonView(Views.Basic.class)
    String mimeType;

    @JsonView({Views.Basic.class,Views.Update.class})
    String alt;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String contentType;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    long fileSize;

    /** Read only: where the site uses it; it is deleted only once unused. */
    @With
    @JsonView(Views.Basic.class)
    List<String> usedBy;
}
