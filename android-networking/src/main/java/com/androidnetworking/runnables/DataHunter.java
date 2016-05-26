/*
 *    Copyright (C) 2016 Amit Shekhar
 *    Copyright (C) 2011 The Android Open Source Project
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.androidnetworking.runnables;

import android.util.Log;

import com.androidnetworking.common.AndroidNetworkingData;
import com.androidnetworking.common.AndroidNetworkingRequest;
import com.androidnetworking.common.AndroidNetworkingResponse;
import com.androidnetworking.common.Constants;
import com.androidnetworking.common.Priority;
import com.androidnetworking.core.Core;
import com.androidnetworking.error.AndroidNetworkingError;
import com.androidnetworking.internal.AndroidNetworkingOkHttp;

import java.io.IOException;

import static com.androidnetworking.common.RequestType.DOWNLOAD;
import static com.androidnetworking.common.RequestType.MULTIPART;
import static com.androidnetworking.common.RequestType.SIMPLE;

/**
 * Created by amitshekhar on 22/03/16.
 */
public class DataHunter implements Runnable {

    private static final String TAG = DataHunter.class.getSimpleName();
    private final Priority priority;
    public final int sequence;
    public final AndroidNetworkingRequest request;

    public DataHunter(AndroidNetworkingRequest request) {
        this.request = request;
        this.sequence = request.getSequenceNumber();
        this.priority = request.getPriority();
    }

    @Override
    public void run() {
        Log.d("execution started : ", request.toString());
        switch (request.getRequestType()) {
            case SIMPLE:
                goForSimpleRequest();
                break;
            case DOWNLOAD:
                goForDownloadRequest();
                break;
            case MULTIPART:
                goForUploadRequest();
                break;
        }
        Log.d("execution done : ", request.toString());
    }

    private void goForSimpleRequest() {
        AndroidNetworkingData data = null;
        try {
            data = AndroidNetworkingOkHttp.performSimpleRequest(request);
            if (data.code == 304) {
                request.finish();
                return;
            }
            if (data.code >= 400) {
                AndroidNetworkingError error = new AndroidNetworkingError(data);
                error = request.parseNetworkError(error);
                error.setErrorCode(data.code);
                error.setErrorDetail(Constants.RESPONSE_FROM_SERVER_ERROR);
                deliverError(request, error);
                return;
            }

            AndroidNetworkingResponse response = request.parseResponse(data);
            if (!response.isSuccess()) {
                deliverError(request, response.getError());
                return;
            }
            request.deliverResponse(response);
        } catch (AndroidNetworkingError se) {
            se = request.parseNetworkError(se);
            se.setErrorDetail(Constants.CONNECTION_ERROR);
            se.setErrorCode(0);
            deliverError(request, se);
        } catch (Exception e) {
            AndroidNetworkingError se = new AndroidNetworkingError(e);
            se.setErrorDetail(Constants.CONNECTION_ERROR);
            se.setErrorCode(0);
            deliverError(request, se);

        } finally {
            if (data != null && data.source != null) {
                try {
                    data.source.close();
                } catch (IOException ignored) {
                    Log.d(TAG, "Unable to close source data");
                }
            }
        }
    }

    private void goForDownloadRequest() {
        AndroidNetworkingData data = null;
        try {
            data = AndroidNetworkingOkHttp.performDownloadRequest(request);
            if (data.code >= 400) {
                AndroidNetworkingError error = new AndroidNetworkingError();
                error = request.parseNetworkError(error);
                error.setErrorCode(data.code);
                error.setErrorDetail(Constants.RESPONSE_FROM_SERVER_ERROR);
                deliverError(request, error);
            }
        } catch (AndroidNetworkingError se) {
            se.setErrorDetail(Constants.CONNECTION_ERROR);
            se.setErrorCode(0);
            deliverError(request, se);
        } catch (Exception e) {
            AndroidNetworkingError se = new AndroidNetworkingError(e);
            se.setErrorDetail(Constants.CONNECTION_ERROR);
            se.setErrorCode(0);
            deliverError(request, se);
        }
    }

    private void goForUploadRequest() {
        AndroidNetworkingData data = null;
        try {
            data = AndroidNetworkingOkHttp.performUploadRequest(request);
            if (data.code == 304) {
                request.finish();
                return;
            }
            if (data.code >= 400) {
                AndroidNetworkingError error = new AndroidNetworkingError(data);
                error = request.parseNetworkError(error);
                error.setErrorCode(data.code);
                error.setErrorDetail(Constants.RESPONSE_FROM_SERVER_ERROR);
                deliverError(request, error);
                return;
            }
            AndroidNetworkingResponse response = request.parseResponse(data);
            if (!response.isSuccess()) {
                deliverError(request, response.getError());
                return;
            }
            request.deliverResponse(response);
        } catch (AndroidNetworkingError se) {
            se = request.parseNetworkError(se);
            se.setErrorDetail(Constants.CONNECTION_ERROR);
            se.setErrorCode(0);
            deliverError(request, se);
        } catch (Exception e) {
            AndroidNetworkingError se = new AndroidNetworkingError(e);
            se.setErrorDetail(Constants.CONNECTION_ERROR);
            se.setErrorCode(0);
            deliverError(request, se);
        } finally {
            if (data != null && data.source != null) {
                try {
                    data.source.close();
                } catch (IOException ignored) {
                    Log.d(TAG, "Unable to close source data");
                }
            }
        }
    }

    public Priority getPriority() {
        return priority;
    }

    private void deliverError(final AndroidNetworkingRequest request, final AndroidNetworkingError error) {
        Core.getInstance().getExecutorSupplier().forMainThreadTasks().execute(new Runnable() {
            public void run() {
                request.deliverError(error);
                request.finish();
            }
        });
    }
}