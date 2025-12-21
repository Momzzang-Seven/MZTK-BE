package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;

@Builder
public record KakaoUserInfo (
        String kakaoId,
        String email,
        String nickname,
        String profileImageUrl
) {
}
