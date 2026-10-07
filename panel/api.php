<?php
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
$d = json_decode((string)@file_get_contents(__DIR__ . '/data.json'), true) ?: [];
echo json_encode([
    'matches'        => $d['matches']        ?? [],
    'movies'         => $d['movies']         ?? [],
    'channels'       => $d['channels']       ?? [],
    'tournament_ids' => $d['tournament_ids'] ?? [572],
    'app_config'     => $d['app_config']     ?? null,
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
