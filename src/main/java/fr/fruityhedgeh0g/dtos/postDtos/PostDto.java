package fr.fruityhedgeh0g.dtos.postDtos;

import com.fasterxml.jackson.annotation.JsonView;
import fr.fruityhedgeh0g.dtos.mediaDtos.NestedMediaDto;
import fr.fruityhedgeh0g.dtos.userDtos.NestedUserDto;
import fr.fruityhedgeh0g.enums.PostStatusEnum;
import fr.fruityhedgeh0g.dtos.Views;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
public class PostDto {

    @JsonView({Views.Minimal.class,Views.CreationResponse.class,Views.Update.class})
    UUID postId;

    @JsonView({Views.Basic.class,Views.Creation.class,Views.Update.class})
    String  title;

    /** Read only: a new Post is Brouillon, then changed through PUT /api/posts/{id}/status. */
    @JsonView(Views.Basic.class)
    PostStatusEnum status;

    /** Its Secteur; none for the whole site. Chosen by the Super admin only, others write for their own Secteur. */
    @JsonView({Views.Basic.class, Views.Creation.class})
    UUID sectorId;

    /** Read only: the Bureau member who created the Post. */
    @JsonView(Views.Basic.class)
    NestedUserDto author;

    @JsonView({Views.Detailed.class,Views.Creation.class,Views.Update.class})
    String content;

    @JsonView({Views.Detailed.class})
    NestedMediaDto banner;

    @JsonView({Views.Detailed.class})
    List<NestedMediaDto> attachments;
}
