// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_OFF;

import android.content.Context;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import io.flutter.view.TextureRegistry;
import androidx.media3.common.Tracks;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import android.util.Log;

@UnstableApi
final class VideoPlayer {
    private ExoPlayer exoPlayer;
    private Surface surface;
    private final TextureRegistry.SurfaceTextureEntry textureEntry;
    private final VideoPlayerCallbacks videoPlayerEvents;
    private final VideoPlayerOptions options;

    private final DefaultTrackSelector trackSelector;

    /**
     * Creates a video player.
     *
     * @param context      application context.
     * @param events       event callbacks.
     * @param textureEntry texture to render to.
     * @param asset        asset to play.
     * @param options      options for playback.
     * @return a video player instance.
     */
    @NonNull
    static VideoPlayer create(Context context, VideoPlayerCallbacks events, TextureRegistry.SurfaceTextureEntry textureEntry, VideoAsset asset, VideoPlayerOptions options) {
        ExoPlayer.Builder builder = new ExoPlayer.Builder(context).setMediaSourceFactory(asset.getMediaSourceFactory(context));
        return new VideoPlayer(context, builder, events, textureEntry, asset.getMediaItem(), options);
    }

    @VisibleForTesting
    VideoPlayer(Context context, ExoPlayer.Builder builder, VideoPlayerCallbacks events, TextureRegistry.SurfaceTextureEntry textureEntry, MediaItem mediaItem, VideoPlayerOptions options) {
        this.videoPlayerEvents = events;
        this.textureEntry = textureEntry;
        this.options = options;
        this.trackSelector = new DefaultTrackSelector(context);

        ExoPlayer exoPlayer = builder.setTrackSelector(trackSelector).build();
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();

        setUpVideoPlayer(exoPlayer);
    }

    private void setUpVideoPlayer(ExoPlayer exoPlayer) {
        this.exoPlayer = exoPlayer;

        surface = new Surface(textureEntry.surfaceTexture());
        exoPlayer.setVideoSurface(surface);
        setAudioAttributes(exoPlayer, options.mixWithOthers);
        exoPlayer.addListener(new ExoPlayerEventListener(exoPlayer, videoPlayerEvents));
    }

    void sendBufferingUpdate() {
        videoPlayerEvents.onBufferingUpdate(exoPlayer.getBufferedPosition());
    }

    private static void setAudioAttributes(ExoPlayer exoPlayer, boolean isMixMode) {
        exoPlayer.setAudioAttributes(new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), !isMixMode);
    }

    void play() {
        exoPlayer.setPlayWhenReady(true);
    }

    void pause() {
        exoPlayer.setPlayWhenReady(false);
    }

    void setLooping(boolean value) {
        exoPlayer.setRepeatMode(value ? REPEAT_MODE_ALL : REPEAT_MODE_OFF);
    }

    void setVolume(double value) {
        float bracketedValue = (float) Math.max(0.0, Math.min(1.0, value));
        exoPlayer.setVolume(bracketedValue);
    }

    void changeBandWidth(double value) {
        if (value != 0) {
            final DefaultTrackSelector.Parameters.Builder params = trackSelector
                    .buildUponParameters()
                    .setMaxVideoBitrate((int) value)
                    .setForceHighestSupportedBitrate(true);
            trackSelector.setParameters(params);
        }
        System.out.println("Xin chào, thế giới 2!"+value);
    }

    List<Map<String, Object>> getVideoResolution() {
        Tracks currentTracks = exoPlayer.getCurrentTracks();
        List<Map<String, Object>> videoResolutions = new ArrayList<>();

        for (Tracks.Group group : currentTracks.getGroups()) {
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (MimeTypes.isVideo(format.sampleMimeType)) {
                    int width = format.width;
                    int height = format.height;
                    int bitrate = format.bitrate;
                    Map<String, Object> resolutionMap = new HashMap<>();
                    resolutionMap.put("width", width);
                    resolutionMap.put("height", height);
                    resolutionMap.put("bitrate", bitrate);

                    // Thêm vào danh sách
                    videoResolutions.add(resolutionMap);
                    Log.d("VideoResolution", "Độ phân giải video: " + width + "x" + height + "bitrate" + bitrate);
                }
            }
        }

        return videoResolutions;
    }

    void setPlaybackSpeed(double value) {
        // We do not need to consider pitch and skipSilence for now as we do not handle them and
        // therefore never diverge from the default values.
        final PlaybackParameters playbackParameters = new PlaybackParameters(((float) value));

        exoPlayer.setPlaybackParameters(playbackParameters);
    }

    void seekTo(int location) {
        exoPlayer.seekTo(location);
    }

    long getPosition() {
        return exoPlayer.getCurrentPosition();
    }

    void dispose() {
        textureEntry.release();
        if (surface != null) {
            surface.release();
        }
        if (exoPlayer != null) {
            exoPlayer.release();
        }
    }
}
