"""Contract tests only. No model imports, weights, microphone, or real inference."""
import os
import unittest
from unittest.mock import patch, AsyncMock

from fastapi import HTTPException
from starlette.requests import Request
import voice_service as service


def request(body=b"", headers=()):
    async def receive():
        return {"type": "http.request", "body": body, "more_body": False}
    return Request({"type": "http", "headers": list(headers), "method": "POST", "path": "/"}, receive)


class VoiceContractTest(unittest.IsolatedAsyncioTestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {"ECHO_VOICE_KEY": ""})
        self.env.start()

    def tearDown(self):
        self.env.stop()

    def test_health_does_not_load_models(self):
        result = service.health()
        self.assertFalse(result["parakeet_loaded"])
        self.assertFalse(result["kokoro_loaded"])

    async def test_auth_gate(self):
        with patch.dict(os.environ, {"ECHO_VOICE_KEY": "test-secret"}):
            next_handler = AsyncMock(return_value="accepted")
            self.assertEqual((await service.auth(request(), next_handler)).status_code, 401)
            next_handler.assert_not_called()
            self.assertEqual(await service.auth(request(headers=[(b"authorization", b"Bearer test-secret")]), next_handler), "accepted")

    async def test_audio_size_cap_before_inference(self):
        with patch.object(service, "run_in_threadpool", new_callable=AsyncMock) as model:
            with self.assertRaises(HTTPException) as error:
                await service.transcribe(request(b"x" * 500001))
            self.assertEqual(error.exception.status_code, 413)
            model.assert_not_called()

    async def test_stt_response_contract_with_mock(self):
        with patch.object(service, "run_in_threadpool", new_callable=AsyncMock, return_value={"text": "follow me"}) as model:
            response = await service.transcribe(request(b"fixture"))
            self.assertEqual(response, {"text": "follow me"})
            model.assert_called_once_with(service.transcribe_wav, b"fixture")

    async def test_speech_limits_before_inference(self):
        with patch.object(service, "run_in_threadpool", new_callable=AsyncMock) as model:
            import json
            for body, status in [(json.dumps({"text": "x" * 601}).encode(), 400), (b"x" * 8193, 413)]:
                with self.assertRaises(HTTPException) as error:
                    await service.speech(request(body))
                self.assertEqual(error.exception.status_code, status)
            model.assert_not_called()

    async def test_tts_response_contract_with_mock(self):
        with patch.object(service, "run_in_threadpool", new_callable=AsyncMock, return_value=b"RIFFfixture"):
            response = await service.speech(request(b'{"text":"Hello"}'))
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.headers["content-type"], "audio/wav")


if __name__ == "__main__":
    unittest.main()
