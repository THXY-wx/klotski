package com.thxy.gamestate

/**
 * 一次成功的移动及其反方向。
 *
 * @property forward 原移动方向。
 * @property inverse 撤销该移动所需的方向。
 */
internal data class Move(val forward: MoveDirection, val inverse: MoveDirection)
