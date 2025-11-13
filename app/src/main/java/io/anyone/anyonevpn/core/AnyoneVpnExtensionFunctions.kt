package io.anyone.anyonevpn.core

import android.content.Intent
import io.anyone.anyonevpn.service.AnyoneVpnConstants

/**
 * Used to build Intents in Anyone VPN, annoyingly we have to set this when passing Intents to
 * AnyoneVpnService to distinguish between Intents that are triggered from this codebase VS
 * Intents that the system sends to the VPNService on boot...
 */
fun Intent.putNotSystem(): Intent = this.putExtra(AnyoneVpnConstants.EXTRA_NOT_SYSTEM, true)
