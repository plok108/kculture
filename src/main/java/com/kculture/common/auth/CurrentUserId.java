package com.kculture.common.auth;

import java.lang.annotation.*;

// 컨트롤러 메서드 파라미터에 붙이면 Authorization 헤더의 토큰으로 식별된 userId가 주입된다.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
