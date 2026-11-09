package com.gh4a;

import android.text.TextUtils;

import com.meisolsson.githubsdk.core.ServiceGenerator;
import com.meisolsson.githubsdk.model.ClientErrorResponse;
import com.squareup.moshi.JsonDataException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Response;

public class ApiRequestException extends RuntimeException {
    private static final long serialVersionUID = -4331443972707730572L;

    private final ClientErrorResponse mResponse;
    private final int mStatus;

    public ApiRequestException(Response response) {
        mStatus = response.code();

        ClientErrorResponse error = null;
        try {
            error = ServiceGenerator.moshi
                    .adapter(ClientErrorResponse.class)
                    .fromJson(response.errorBody().source());
        } catch (IOException | JsonDataException e) {
            // 225：JsonDataException 是 RuntimeException（moshi 1.15.2
            // javap 核实），不是 IOException 子类；错误体为合法 JSON 但
            // 类型错配时（如 message 为数字）此前会直接逃逸，status 码、
            // 服务端错误文案、401 登出、DMCA 分支全部走不到。解析失败时
            // mResponse 置 null 降级（getMessage 走 HTTP status 分支）。
        }
        mResponse = error;
    }

    public int getStatus() {
        return mStatus;
    }

    public ClientErrorResponse getResponse() {
        return mResponse;
    }

    @Override
    public String getMessage() {
        if (mResponse == null) {
            return super.getMessage();
        }

        String message = mResponse.message();
        List<String> errors = new ArrayList<>();

        List<ClientErrorResponse.FieldError> fieldErrors = mResponse.errors();
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            for (ClientErrorResponse.FieldError e : fieldErrors) {
                String errorMessage = formatFieldError(e);
                if (!TextUtils.isEmpty(errorMessage)) {
                    errors.add(errorMessage);
                }
            }
        }

        if (!TextUtils.isEmpty(message) && !errors.isEmpty()) {
            return String.format(Locale.US, "%1$s (%2$d) [%3$s]",
                    message, mStatus, TextUtils.join(", ", errors));
        } else if (!TextUtils.isEmpty(message)) {
            return String.format(Locale.US, "%1$s (%2$d)", message, mStatus);
        } else {
            return "HTTP status " + mStatus;
        }
    }

    private String formatFieldError(ClientErrorResponse.FieldError error) {
        // 194：error.reason() 可空，switch (null) 即 NPE；无 reason 时
        // 返回 null，调用方按“无此条错误文案”跳过（与方法末尾默认一致）。
        if (error.reason() == null) {
            return null;
        }
        switch (error.reason()) {
            case Invalid:
                return String.format("Value for field %1$s is invalid", error.field());
            case MissingField:
                return String.format("Value for required field %1$s is missing", error.field());
            case MissingResource:
                return String.format("Resource %1$s does not exist", error.resource());
            case AlreadyExists:
                return String.format(
                        "A resource of type '%1$s' with the same value in field %2$s already exists",
                        error.resource(), error.field());
            case TooLarge:
                return String.format("The field %1$s was too large", error.field());
            case Custom:
                return error.message();
        }
        return null;
    }
}
