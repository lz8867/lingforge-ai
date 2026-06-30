package com.example.demo.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import ai.z.openapi.service.videos.VideoCreateParams;
import ai.z.openapi.service.videos.VideosResponse;

import java.util.Arrays;

public class QuickStart {
    public static void main(String[] args) {
        // 初始化客户端
        ZhipuAiClient client = ZhipuAiClient.builder().ofZHIPU()
                .apiKey("0f719fd7c596481c8a294161afcf5f0b.YDgi5D3rJajOy4C2")
                .build();

        // 创建聊天完成请求
        VideoCreateParams request = VideoCreateParams.builder()
                .model("CogVideoX-2").prompt("元宵节快乐").quality("quality").withAudio(true).size("1920x1080").fps(30)
                .build();

//         发送请求
//        VideosResponse response = client.videos().videoGenerations(request);
//
////         获取回复
//        System.out.println(response.getData().getId());

        //2026022710050174ec322cd52c43ce

        VideosResponse videosResponse = client.videos().videoGenerationsResult("20260227181018deb677c490ff4cb7");
        System.out.println("---->"+videosResponse);
        System.out.println("---->"+videosResponse.getData().getVideoResult().get(0).getUrl());
        System.out.println("---->"+videosResponse.getData().getVideoResult().get(0).getCoverImageUrl());
    }

}
