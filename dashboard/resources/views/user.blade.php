@extends('layouts.app')

@section('content')
<div class="container">
    <div class="row justify-content-center">
        <div class="col-md-12">
            <div class="card">
                <div class="card-header">
                    User information
                    <a href="" class="stretched-link float-right">
                        <span class="oi oi-pencil" title="pencil"></span>
                        Edit
                    </a>
                </div>

                <div>
                    <ul class="list-group list-group-flush">
                        <li class="list-group-item">
                            Username:
                            <span class="float-right">{{ $user->name }}</span>
                        </li>
                        <li class="list-group-item">
                            Email:
                            <span class="float-right">{{ $user->email }}</span>
                        </li>
                    </ul>
                </div>
            </div>
        </div>
        
    </div>
    
</div>
@endsection
