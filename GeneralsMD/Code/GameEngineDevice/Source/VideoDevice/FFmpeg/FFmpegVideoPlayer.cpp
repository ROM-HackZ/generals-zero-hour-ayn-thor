/*
**	Command & Conquer Generals Zero Hour(tm)
**	Copyright 2025 Electronic Arts Inc.
**
**	This program is free software: you can redistribute it and/or modify
**	it under the terms of the GNU General Public License as published by
**	the Free Software Foundation, either version 3 of the License, or
**	(at your option) any later version.
**
**	This program is distributed in the hope that it will be useful,
**	but WITHOUT ANY WARRANTY; without even the implied warranty of
**	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
**	GNU General Public License for more details.
**
**	You should have received a copy of the GNU General Public License
**	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

////////////////////////////////////////////////////////////////////////////////
//																																						//
//  (c) 2001-2003 Electronic Arts Inc.																				//
//																																						//
////////////////////////////////////////////////////////////////////////////////

//----------------------------------------------------------------------------
//                                                                          
//                       Westwood Studios Pacific.                          
//                                                                          
//                       Confidential Information                           
//                Copyright (C) 2001 - All Rights Reserved                  
//                                                                          
//----------------------------------------------------------------------------
//
// Project:   Generals
//
// Module:    VideoDevice
//
// File name: FFmpegVideoPlayer.cpp
//
// Created:   03/13/24	SV
//
//----------------------------------------------------------------------------

//----------------------------------------------------------------------------
//         Includes                                                      
//----------------------------------------------------------------------------

#include "Lib/BaseType.h"
#include "GXTrace.h"
#include <cstdio>
#include "VideoDevice/FFmpeg/FFmpegVideoPlayer.h"
#include "Common/AudioAffect.h"
#include "Common/GameAudio.h"
#include "Common/GameMemory.h"
#include "Common/GlobalData.h"
#include "Common/Registry.h"
#include "Common/FileSystem.h"

#include "VideoDevice/FFmpeg/FFmpegFile.h"  

extern "C" {
    #include <libavcodec/avcodec.h>
    #include <libswscale/swscale.h>
}

#ifdef SAGE_USE_OPENAL
#include "OpenALAudioDevice/OpenALAudioManager.h"
#include "OpenALAudioDevice/OpenALAudioStream.h"
#endif

#include <chrono>

//----------------------------------------------------------------------------
//         Externals                                                     
//----------------------------------------------------------------------------


 
//----------------------------------------------------------------------------
//         Defines                                                         
//----------------------------------------------------------------------------
#define VIDEO_LANG_PATH_FORMAT "Data/%s/Movies/%s.%s"
#define VIDEO_PATH	"Data\\Movies"
#define VIDEO_EXT		"bik"


//----------------------------------------------------------------------------
//         Private Types                                                     
//----------------------------------------------------------------------------



//----------------------------------------------------------------------------
//         Private Data                                                     
//----------------------------------------------------------------------------



//----------------------------------------------------------------------------
//         Public Data                                                      
//----------------------------------------------------------------------------



//----------------------------------------------------------------------------
//         Private Prototypes                                               
//----------------------------------------------------------------------------



//----------------------------------------------------------------------------
//         Private Functions                                               
//----------------------------------------------------------------------------



//----------------------------------------------------------------------------
//         Public Functions                                                
//----------------------------------------------------------------------------


//============================================================================
// FFmpegVideoPlayer::FFmpegVideoPlayer
//============================================================================

FFmpegVideoPlayer::FFmpegVideoPlayer()
{

}

//============================================================================
// FFmpegVideoPlayer::~FFmpegVideoPlayer
//============================================================================

FFmpegVideoPlayer::~FFmpegVideoPlayer()
{
	deinit();
}

//============================================================================
// FFmpegVideoPlayer::init
//============================================================================

void	FFmpegVideoPlayer::init( void )
{
	// Need to load the stuff from the ini file.
	VideoPlayer::init();

	initializeBinkWithMiles();
}

//============================================================================
// FFmpegVideoPlayer::deinit
//============================================================================

void FFmpegVideoPlayer::deinit( void )
{
	TheAudio->releaseHandleForBink();
	VideoPlayer::deinit();
}

//============================================================================
// FFmpegVideoPlayer::reset
//============================================================================

void	FFmpegVideoPlayer::reset( void )
{
	VideoPlayer::reset();
}

//============================================================================
// FFmpegVideoPlayer::update
//============================================================================

void	FFmpegVideoPlayer::update( void )
{
	VideoPlayer::update();

}

//============================================================================
// FFmpegVideoPlayer::loseFocus
//============================================================================

void	FFmpegVideoPlayer::loseFocus( void )
{
	VideoPlayer::loseFocus();
}

//============================================================================
// FFmpegVideoPlayer::regainFocus
//============================================================================

void	FFmpegVideoPlayer::regainFocus( void )
{
	VideoPlayer::regainFocus();
}

//============================================================================
// FFmpegVideoPlayer::createStream
//============================================================================

VideoStreamInterface* FFmpegVideoPlayer::createStream( File* file )
{

	if ( file == NULL )
	{
		return NULL;
	}

    FFmpegFile* ffmpegHandle = NEW FFmpegFile();
    if(!ffmpegHandle->open(file))
    {
        delete ffmpegHandle;
        return NULL;
    }

	FFmpegVideoStream *stream = NEW FFmpegVideoStream(ffmpegHandle);

	if ( stream )
	{
		stream->m_next = m_firstStream;
		stream->m_player = this;
		m_firstStream = stream;

		// never let volume go to 0, as Bink will interpret that as "play at full volume".
		Int mod = (Int) ((TheAudio->getVolume(AudioAffect_Speech) * 0.8f) * 100) + 1;
        [[maybe_unused]]  Int volume = (32768 * mod) / 100;
		DEBUG_LOG(("FFmpegVideoPlayer::createStream() - About to set volume (%g -> %d -> %d\n",
			TheAudio->getVolume(AudioAffect_Speech), mod, volume));
		//BinkSetVolume( stream->m_handle,0, volume);
		DEBUG_LOG(("FFmpegVideoPlayer::createStream() - set volume\n"));
	}

	return stream;
}

//============================================================================
// FFmpegVideoPlayer::open
//============================================================================

VideoStreamInterface*	FFmpegVideoPlayer::open( AsciiString movieTitle )
{
	VideoStreamInterface*	stream = NULL;

	const Video* pVideo = getVideo(movieTitle);
	if (pVideo) {
		DEBUG_LOG(("FFmpegVideoPlayer::createStream() - About to open bink file\n"));
		
		if (TheGlobalData->m_modDir.isNotEmpty())
		{
			char filePath[ _MAX_PATH ];
			sprintf( filePath, "%s%s\\%s.%s", TheGlobalData->m_modDir.str(), VIDEO_PATH, pVideo->m_filename.str(), VIDEO_EXT );
			File* file =  TheFileSystem->openFile(filePath);
            DEBUG_ASSERTLOG(!file, ("opened bink file %s\n", filePath));
            if (file)
            {
                return createStream( file );
            }
		}

		char localizedFilePath[ _MAX_PATH ];
		sprintf( localizedFilePath, VIDEO_LANG_PATH_FORMAT, GetRegistryLanguage().str(), pVideo->m_filename.str(), VIDEO_EXT );
        File* file =  TheFileSystem->openFile(localizedFilePath);
		DEBUG_ASSERTLOG(!file, ("opened localized bink file %s\n", localizedFilePath));
		if (!file)
		{
			char filePath[ _MAX_PATH ];
			sprintf( filePath, "%s\\%s.%s", VIDEO_PATH, pVideo->m_filename.str(), VIDEO_EXT );
			file = TheFileSystem->openFile(filePath);
			DEBUG_ASSERTLOG(!file, ("opened bink file %s\n", filePath));
		}

		DEBUG_LOG(("FFmpegVideoPlayer::createStream() - About to create stream\n"));
        stream = createStream( file );
	}

	return stream;	
}

//============================================================================
// FFmpegVideoPlayer::load
//============================================================================

VideoStreamInterface*	FFmpegVideoPlayer::load( AsciiString movieTitle )
{
	return open(movieTitle); // load() used to have the same body as open(), so I'm combining them.  Munkee.
}

//============================================================================
//============================================================================
void FFmpegVideoPlayer::notifyVideoPlayerOfNewProvider( Bool nowHasValid )
{
	if (!nowHasValid) {
		TheAudio->releaseHandleForBink();
		//BinkSetSoundTrack(0, 0);
	} else {
		initializeBinkWithMiles();
	}
}

//============================================================================
//============================================================================
void FFmpegVideoPlayer::initializeBinkWithMiles()
{
	Int retVal = 0;
	void *driver = TheAudio->getHandleForBink();	
	
	if ( driver )
	{
		//retVal = BinkSoundUseDirectSound(driver);
	}
	if( !driver || retVal == 0)
	{
		//BinkSetSoundTrack ( 0,0 );
	}
}

//============================================================================
// FFmpegVideoStream::FFmpegVideoStream
//============================================================================

FFmpegVideoStream::FFmpegVideoStream(FFmpegFile* file)
: m_ffmpegFile(file)
{
    m_ffmpegFile->setFrameCallback(onFrame);
    m_ffmpegFile->setUserData(this);

#ifdef SAGE_USE_OPENAL
    // Release the audio handle if it's already in use
    OpenALAudioStream* audioStream = (OpenALAudioStream*)TheAudio->getHandleForBink();
    // GeneralsX @feature Android port 09/09/2026 Measure the tail the previous movie lost.
    // reset() is alSourceStop() + unqueue-everything, so whatever the last movie still had
    // buffered ahead of the speaker is discarded here. The movie's audio queue runs a decode
    // burst ahead of playback, so this is how much of the previous movie was never heard.
    ALint gxLeftOver = 0, gxPrevState = 0;
    alGetSourcei(audioStream->getSource(), AL_BUFFERS_QUEUED, &gxLeftOver);
    alGetSourcei(audioStream->getSource(), AL_SOURCE_STATE, &gxPrevState);
    GX_AUDIO_TRACE("new movie: dropping %d queued buffer(s) from the previous stream (state=%s)\n",
            (int)gxLeftOver,
            gxPrevState == AL_PLAYING ? "PLAYING" :
            gxPrevState == AL_STOPPED ? "STOPPED" :
            gxPrevState == AL_PAUSED  ? "PAUSED"  : "INITIAL");
    audioStream->reset();
#endif

    // GeneralsX @bugfix Codex 18/10/2026 Prime real audio before starting the common clock.
    // update() buffers both streams without replacing the frame being displayed.
    update();
    frameNext();
    m_startTime = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    m_started = true;
#ifdef SAGE_USE_OPENAL
    audioStream->setVolume(1.0f);
    audioStream->update();
#endif
    fprintf(stderr, "[GX-VIDEO] start frames=%d frame_ms=%.6f first=%d buffered=%zu\n",
        frameCount(), m_ffmpegFile->getFrameTime(), m_frameIndex, m_videoFrames.size());
}

//============================================================================
// FFmpegVideoStream::~FFmpegVideoStream
//============================================================================

FFmpegVideoStream::~FFmpegVideoStream()
{
    // GeneralsX @bugfix Codex 18/10/2026 Skipping frees queued pictures as well as the current one.
    const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    fprintf(stderr, "[GX-VIDEO] close presented=%d index=%d frames=%d decoded=%d elapsed_ms=%llu\n",
        m_renderedFrames, frameIndex(), frameCount(), m_ffmpegFile->getCurrentFrame(),
        (unsigned long long)(now - m_startTime));
    for (auto* frame : m_videoFrames) av_frame_free(&frame);
    m_videoFrames.clear();
    // GeneralsX @bugfix fbraz3 20/04/2026 Stop and clear queued OpenAL buffers so video audio
    // does not bleed into gameplay after the loading screen closes the stream.
    // Issue: https://github.com/fbraz3/GeneralsX/issues/38
#ifdef SAGE_USE_OPENAL
    if (TheAudio)
    {
        OpenALAudioStream* audioStream = (OpenALAudioStream*)TheAudio->getHandleForBink();
        if (audioStream)
            audioStream->reset();
    }
#endif
    av_freep(&m_audioBuffer);
    av_frame_free(&m_frame);
    sws_freeContext(m_swsContext);
    delete m_ffmpegFile;
}

void FFmpegVideoStream::onFrame(AVFrame *frame, int stream_idx, int stream_type, void *user_data)
{
    FFmpegVideoStream *videoStream = static_cast<FFmpegVideoStream *>(user_data);
    if (stream_type == AVMEDIA_TYPE_VIDEO) {
        // GeneralsX @bugfix Codex 18/10/2026 Keep decoded video in FIFO order while audio reads ahead.
        AVFrame* copy = av_frame_clone(frame);
        if (copy) videoStream->m_videoFrames.push_back(copy);
    }
#ifdef SAGE_USE_OPENAL
    else if (stream_type == AVMEDIA_TYPE_AUDIO) {
        // GeneralsX @feature Android port 08/09/2026 This path is the ONLY one that ever
        // produces movie audio -- it is driven directly by FFmpegVideoStream's own decode
        // loop, not through OpenALAudioManager's streaming lambda (setRequireDataCallback is
        // never called on this stream). Trace what the source actually holds, so the queue
        // depth the fix below is about is visible in a device log.
        OpenALAudioStream* audioStream = (OpenALAudioStream*)TheAudio->getHandleForBink();

        AVSampleFormat sampleFmt = static_cast<AVSampleFormat>(frame->format);
        const int bytesPerSample = av_get_bytes_per_sample(sampleFmt);
        const int frameSize =
            av_samples_get_buffer_size(NULL, frame->ch_layout.nb_channels, frame->nb_samples, sampleFmt, 1);
        if (frameSize <= 0 || bytesPerSample <= 0 || frame->ch_layout.nb_channels <= 0 || frame->nb_samples <= 0) {
            return;
        }

        int outputBitsPerSample = bytesPerSample * 8;
        int outputFrameSize = frameSize;
        uint8_t* frameData = frame->data[0];

        // GeneralsX @bugfix BenderAI 22/04/2026 Convert float32 FFmpeg output to PCM16 for robust OpenAL compatibility.
        if (sampleFmt == AV_SAMPLE_FMT_FLT || sampleFmt == AV_SAMPLE_FMT_FLTP)
        {
            outputBitsPerSample = 16;
            outputFrameSize = frame->nb_samples * frame->ch_layout.nb_channels * (int)sizeof(int16_t);
            videoStream->m_audioBuffer = static_cast<uint8_t*>(av_realloc(videoStream->m_audioBuffer, outputFrameSize));
            if (videoStream->m_audioBuffer == nullptr)
            {
                DEBUG_LOG(("Failed to allocate converted audio buffer"));
                return;
            }

            int16_t *dst = reinterpret_cast<int16_t *>(videoStream->m_audioBuffer);
            if (sampleFmt == AV_SAMPLE_FMT_FLTP)
            {
                for (int sample_idx = 0; sample_idx < frame->nb_samples; ++sample_idx)
                {
                    for (int channel_idx = 0; channel_idx < frame->ch_layout.nb_channels; ++channel_idx)
                    {
                        const float sample = reinterpret_cast<const float *>(frame->data[channel_idx])[sample_idx];
                        const float clamped = (sample < -1.0f) ? -1.0f : ((sample > 1.0f) ? 1.0f : sample);
                        *dst++ = static_cast<int16_t>(clamped * 32767.0f);
                    }
                }
            }
            else
            {
                const float *src = reinterpret_cast<const float *>(frame->data[0]);
                const int totalSamples = frame->nb_samples * frame->ch_layout.nb_channels;
                for (int i = 0; i < totalSamples; ++i)
                {
                    const float clamped = (src[i] < -1.0f) ? -1.0f : ((src[i] > 1.0f) ? 1.0f : src[i]);
                    *dst++ = static_cast<int16_t>(clamped * 32767.0f);
                }
            }

            frameData = videoStream->m_audioBuffer;
        }
        // The format is planar - convert it to interleaved
        else if (av_sample_fmt_is_planar(sampleFmt))
        {
            videoStream->m_audioBuffer = static_cast<uint8_t*>(av_realloc(videoStream->m_audioBuffer, outputFrameSize));
            if (videoStream->m_audioBuffer == nullptr)
            {
                DEBUG_LOG(("Failed to allocate audio buffer"));
                return;
            }

            // Write the samples into our audio buffer
            for (int sample_idx = 0; sample_idx < frame->nb_samples; sample_idx++)
            {
                int byte_offset = sample_idx * bytesPerSample;
                for (int channel_idx = 0; channel_idx < frame->ch_layout.nb_channels; channel_idx++)
                {
                    uint8_t* dst =
                        &videoStream
                             ->m_audioBuffer[byte_offset * frame->ch_layout.nb_channels + channel_idx * bytesPerSample];
                    uint8_t* src = &frame->data[channel_idx][byte_offset];
                    memcpy(dst, src, bytesPerSample);
                }
            }
            frameData = videoStream->m_audioBuffer;
        }

        ALenum format = OpenALAudioManager::getALFormat(frame->ch_layout.nb_channels, outputBitsPerSample);

        // GeneralsX @bugfix Codex 18/10/2026 Buffer actual samples; silence delayed dialogue and cut off its tail.
        // GeneralsX @feature Android port 09/09/2026 Peak amplitude of the PCM we are about to
        // queue. This is the one thing the device logs could never answer: whether the silence
        // is in the pipeline or in the file. peak==0 across a whole movie means the decoder is
        // handing us digital silence; a healthy track peaks in the thousands.
        static int gxPeakSinceTrace = 0;
        if (outputBitsPerSample == 16) {
            const int16_t* gxPcm = reinterpret_cast<const int16_t*>(frameData);
            const int gxCount = outputFrameSize / (int)sizeof(int16_t);
            for (int i = 0; i < gxCount; ++i) {
                const int v = gxPcm[i] < 0 ? -(int)gxPcm[i] : (int)gxPcm[i];
                if (v > gxPeakSinceTrace)
                    gxPeakSinceTrace = v;
            }
        }

        if (!audioStream->bufferData(frameData, outputFrameSize, format, frame->sample_rate)) {
            GX_AUDIO_TRACE("movie audio frame: bufferData() FAILED (format=0x%x rate=%d size=%d)\n",
                    (unsigned)format, frame->sample_rate, outputFrameSize);
        }
        // Source maintenance runs after the bounded decode batch in update().

        static int gxAudioFrameCount = 0;
        if ((gxAudioFrameCount++ % 30) == 0) {
            ALint gxQueued = 0, gxProcessed = 0, gxState = 0;
            alGetSourcei(audioStream->getSource(), AL_BUFFERS_QUEUED, &gxQueued);
            alGetSourcei(audioStream->getSource(), AL_BUFFERS_PROCESSED, &gxProcessed);
            alGetSourcei(audioStream->getSource(), AL_SOURCE_STATE, &gxState);
            GX_AUDIO_TRACE("movie audio frame #%d: ch=%d samples=%d fmt=%d rate=%d peak=%d queued=%d processed=%d state=%s\n",
                    gxAudioFrameCount, frame->ch_layout.nb_channels, frame->nb_samples,
                    (int)frame->format, frame->sample_rate, gxPeakSinceTrace,
                    (int)gxQueued, (int)gxProcessed,
                    gxState == AL_PLAYING ? "PLAYING" :
                    gxState == AL_STOPPED ? "STOPPED" :
                    gxState == AL_PAUSED  ? "PAUSED"  : "INITIAL");
            gxPeakSinceTrace = 0;
        }
    }
#endif
}


//============================================================================
// FFmpegVideoStream::update
//============================================================================

void FFmpegVideoStream::update( void )
{
    // GeneralsX @bugfix Codex 18/10/2026 Bound audio prefetch and preserve every pending video frame.
#ifdef SAGE_USE_OPENAL
    OpenALAudioStream* audioStream = (OpenALAudioStream*)TheAudio->getHandleForBink();
    if (m_started) audioStream->update();
#endif
    int budget = 48;
    while (m_good && m_videoFrames.size() < 16 && budget-- > 0) {
        bool needAudio = false;
#ifdef SAGE_USE_OPENAL
        ALint queued = 0;
        alGetSourcei(audioStream->getSource(), AL_BUFFERS_QUEUED, &queued);
        needAudio = m_ffmpegFile->hasAudio() && queued < 8;
#endif
        if (!needAudio && !m_videoFrames.empty()) break;
        m_good = m_ffmpegFile->decodePacket();
    }
#ifdef SAGE_USE_OPENAL
    if (m_started) audioStream->update();
#endif
}

//============================================================================
// FFmpegVideoStream::isFrameReady
//============================================================================

Bool FFmpegVideoStream::isFrameReady( void )
{
    // GeneralsX @bugfix Codex 18/10/2026 Schedule the presented frame, not the decoder's look-ahead position.
    const uint64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    return double(now - m_startTime) >= m_ffmpegFile->getFrameTime() * frameIndex();
}

//============================================================================
// FFmpegVideoStream::frameDecompress
//============================================================================

void FFmpegVideoStream::frameDecompress( void )
{
	//BinkDoFrame( m_handle );
}

//============================================================================
// FFmpegVideoStream::frameRender
//============================================================================

void FFmpegVideoStream::frameRender( VideoBuffer *buffer )
{
    if (buffer == nullptr) {
        return;
    }

    if (m_frame == nullptr) {
        return;
    }

    if (m_frame->data == nullptr) {
        return;
    }

    AVPixelFormat dst_pix_fmt;

    switch (buffer->format()) {
        case VideoBuffer::TYPE_R8G8B8:
            dst_pix_fmt = AV_PIX_FMT_RGB24;
            break;
        case VideoBuffer::TYPE_X8R8G8B8:
            dst_pix_fmt = AV_PIX_FMT_BGR0;
            break;
        case VideoBuffer::TYPE_R5G6B5:
            dst_pix_fmt = AV_PIX_FMT_RGB565;
            break;
        case VideoBuffer::TYPE_X1R5G5B5:
            dst_pix_fmt = AV_PIX_FMT_RGB555;
            break;
        default:
            return;
    }

    m_swsContext = sws_getCachedContext(m_swsContext,
        width(),
        height(),
        static_cast<AVPixelFormat>(m_frame->format),
        buffer->width(),
        buffer->height(),
        dst_pix_fmt,
        // GeneralsX @performance Codex 18/10/2026 Match Core's inexpensive Android colorspace conversion.
#if defined(__ANDROID__)
        SWS_FAST_BILINEAR,
#else
        SWS_BICUBIC,
#endif
        NULL,
        NULL,
        NULL);

    uint8_t *buffer_data = static_cast<uint8_t *>(buffer->lock());
    if (buffer_data == nullptr) {
        DEBUG_LOG(("Failed to lock videobuffer"));
        return;
    }

    int dst_strides[] = { (int)buffer->pitch() };
    uint8_t *dst_data[] = { buffer_data };
    [[maybe_unused]] int result =
        sws_scale(m_swsContext, m_frame->data, m_frame->linesize, 0, height(), dst_data, dst_strides);
    DEBUG_ASSERTLOG(result >= 0, ("Failed to scale frame"));
    buffer->unlock();
    // GeneralsX @bugfix Codex 18/10/2026 Trace actual presentation separately from decoder progress.
    if (m_lastRenderedFrame != m_frameIndex) {
        m_lastRenderedFrame = m_frameIndex;
        ++m_renderedFrames;
        GX_AUDIO_TRACE("movie video present=%d decoded=%d buffered=%zu\n",
            m_frameIndex, m_ffmpegFile->getCurrentFrame(), m_videoFrames.size());
    }
}

//============================================================================
// FFmpegVideoStream::frameNext
//============================================================================

void FFmpegVideoStream::frameNext( void )
{
    // GeneralsX @bugfix Codex 18/10/2026 Advance presentation by exactly one frame, even after decoding ahead.
    if (m_videoFrames.empty()) update();
    if (!m_videoFrames.empty()) {
        av_frame_free(&m_frame);
        m_frame = m_videoFrames.front();
        m_videoFrames.pop_front();
        ++m_frameIndex;
    }
}

//============================================================================
// FFmpegVideoStream::frameIndex
//============================================================================

Int FFmpegVideoStream::frameIndex( void )
{
    return m_frameIndex < 0 ? 0 : m_frameIndex;
}

//============================================================================
// FFmpegVideoStream::totalFrames
//============================================================================

Int	FFmpegVideoStream::frameCount( void )
{
    // GeneralsX @bugfix Codex 18/10/2026 A truncated stream still finishes after its last decoded frame.
    return m_good ? m_ffmpegFile->getNumFrames() : frameIndex() + int(m_videoFrames.size()) + 1;
}

//============================================================================
// FFmpegVideoStream::frameGoto
//============================================================================

void FFmpegVideoStream::frameGoto( Int index )
{
    // GeneralsX @bugfix Codex 18/10/2026 A seek discards prefetched pictures and restarts its presentation clock.
    for (auto* frame : m_videoFrames) av_frame_free(&frame);
    m_videoFrames.clear();
    av_frame_free(&m_frame);
#ifdef SAGE_USE_OPENAL
    ((OpenALAudioStream*)TheAudio->getHandleForBink())->reset();
#endif
    m_started = false;
    // GeneralsX @bugfix Codex 19/10/2026 Failed seeks terminate instead of reading an unrelated position.
    index = std::max(0, std::min(index, m_ffmpegFile->getNumFrames() - 1));
    m_good = m_ffmpegFile->seekFrame(index);
    m_frameIndex = index - 1;
    m_lastRenderedFrame = -1;
    update();
    frameNext();
    const uint64_t now = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
    m_startTime = now - uint64_t(m_ffmpegFile->getFrameTime() * frameIndex());
    m_started = true;
}

//============================================================================
// VideoStream::height
//============================================================================

Int		FFmpegVideoStream::height( void )
{
	return m_ffmpegFile->getHeight();
}

//============================================================================
// VideoStream::width
//============================================================================

Int		FFmpegVideoStream::width( void )
{
	return m_ffmpegFile->getWidth();
}



