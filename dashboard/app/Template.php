<?php

namespace App;

use Illuminate\Database\Eloquent\Model;

class Template extends Model
{
    
    /**
     * The attributes that are mass assignable.
     *
     * @var array
     */
    protected $fillable = [
        'name', 'user_id', 'yaml_template', 'json_graph','process_id', 'bpmn_endpoint'
    ];

    /**
     * The attributes that should be hidden for arrays.
     *
     * @var array
     */
    protected $hidden = [
    ];

    /**
     * The attributes that should be cast to native types.
     *
     * @var array
     */
    protected $casts = [
    ];

    /**
     * Returns the owner user
     * 
     */
    public function user()
    {
        return $this->belongsTo('App\User');
    }
}
