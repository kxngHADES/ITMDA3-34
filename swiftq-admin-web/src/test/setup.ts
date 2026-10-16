// Runs before every component test. It gives Node a fake browser (jsdom) and a fake network.
import "./env";
import "global-jsdom/register";
import { setTokenProvider } from "@/lib/api";

// Tests act as a signed-in person without talking to a real server.
setTokenProvider(async () => "test-token");

// jsdom does not implement the <dialog> methods yet, so add simple versions.
const proto = globalThis.HTMLDialogElement?.prototype;
if (proto) {
  proto.showModal = function showModal(this: HTMLDialogElement) { this.setAttribute("open", ""); };
  proto.close = function close(this: HTMLDialogElement) { this.removeAttribute("open"); };
}
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
