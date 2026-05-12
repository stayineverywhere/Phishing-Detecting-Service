package com.example.voiceguard.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AnalysisApi {
    @POST("analysis/stt")
    Call<AnalysisResponse> analyzeStt(@Body AnalysisRequest request);

    @POST("analysis/message")
    Call<AnalysisResponse> analyzeMessage(@Body AnalysisRequest request);

    @GET("history")
    Call<java.util.List<AnalysisResponse>> getHistory();

    @GET("stats")
    Call<StatsResponse> getStats();
}
